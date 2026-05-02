package cafe.core.sim;

import cafe.core.SharedType;
import cafe.core.dsl.ChefProgram;
import cafe.core.dsl.Expression;
import cafe.core.dsl.Instruction;
import cafe.core.dsl.Program;
import cafe.core.dsl.ThreadKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Simulator {

    private static final int MAX_TICKS = 10_000;

    private final Map<String, Integer> globals = new LinkedHashMap<>();
    private final Map<String, String> lockOwner = new HashMap<>();
    private final Map<String, Deque<Integer>> queues = new LinkedHashMap<>();
    private final Map<String, Integer> queueCapacity = new HashMap<>();
    private final List<String> events = new ArrayList<>();
    private final List<ChefState> chefs = new ArrayList<>();
    private int ticks;
    private int nextChefIndex;
    private boolean finished;
    private String error;
    private int lastStepLine;
    private final Map<String, Integer> executorPoolSize = new HashMap<>();
    private final Map<String, Integer> activeExecutorSlots = new HashMap<>();

    public Simulator(Program program, Map<String, Integer> initialGlobals) {
        this(program, initialGlobals, Map.of());
    }

    public Simulator(Program program, Map<String, Integer> initialGlobals,
                     Map<String, SharedType> declarations) {
        globals.putAll(initialGlobals);
        for (var entry : declarations.entrySet()) {
            String name = entry.getKey();
            SharedType type = entry.getValue();
            if (type instanceof SharedType.QueueType q) {
                queues.put(name, new ArrayDeque<>());
                queueCapacity.put(name, q.capacity());
            } else if (type instanceof SharedType.FixedExecutorType ex) {
                executorPoolSize.put(name, ex.poolSize());
            } else if (type instanceof SharedType.VirtualExecutorType) {
                executorPoolSize.put(name, Integer.MAX_VALUE);
            }
        }
        for (ChefProgram chef : program.chefs()) {
            chefs.add(new ChefState(chef));
        }
    }

    public boolean isFinished() {
        return finished;
    }

    /**
     * Source line of the most recently executed instruction, or 0 if no
     * instruction has run yet (e.g., right after init or after a step that
     * only blocked on a lock / pool slot / queue).
     */
    public int lastStepLine() {
        return lastStepLine;
    }

    /** Snapshot of every BlockingQueue's current contents (head first). */
    public Map<String, List<Integer>> queueSnapshot() {
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (var entry : queues.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return result;
    }


    public record ChefSnapshot(int index, String name, boolean done, String blockedOnLock,
                               String lastEventDetail, Map<String, Integer> locals) {
    }

    public List<ChefSnapshot> chefSnapshots() {
        List<ChefSnapshot> result = new ArrayList<>();
        for (int i = 0; i < chefs.size(); i++) {
            ChefState chef = chefs.get(i);
            String blocked = null;
            if (!chef.done()) {
                Instruction nextInstr = chef.peek();
                if (nextInstr instanceof Instruction.Lock l) {
                    String holder = lockOwner.get(l.lockName());
                    if (holder != null && !holder.equals(chef.name())) {
                        blocked = l.lockName();
                    }
                }
            }
            result.add(new ChefSnapshot(i, chef.name(), chef.done(), blocked,
                chef.lastEventDetail, Map.copyOf(chef.locals)));
        }
        return result;
    }

    public boolean stepChef(int index) {
        if (finished) {
            return false;
        }
        if (index < 0 || index >= chefs.size()) {
            return false;
        }
        if (++ticks > MAX_TICKS) {
            finish("Simulation exceeded " + MAX_TICKS + " ticks");
            return false;
        }
        ChefState chef = chefs.get(index);
        if (chef.done()) {
            return false;
        }
        boolean progressed = step(chef);
        if (allChefsDone()) {
            finish(null);
        }
        return progressed;
    }

    private boolean allChefsDone() {
        for (ChefState chef : chefs) {
            if (!chef.done()) {
                return false;
            }
        }
        return true;
    }

    public SimulationResult snapshot() {
        return new SimulationResult(events, globals, error);
    }

    public SimulationResult runToCompletion() {
        while (stepRound()) {
            // loop
        }
        return snapshot();
    }

    public boolean stepInstruction() {
        if (finished) {
            return false;
        }
        if (++ticks > MAX_TICKS) {
            finish("Simulation exceeded " + MAX_TICKS + " ticks");
            return false;
        }
        int n = chefs.size();
        for (int tries = 0; tries < n; tries++) {
            if (nextChefIndex >= n) {
                nextChefIndex = 0;
            }
            ChefState chef = chefs.get(nextChefIndex);
            nextChefIndex++;
            if (chef.done()) {
                continue;
            }
            if (step(chef)) {
                return true;
            }
        }
        boolean anyAlive = false;
        for (ChefState chef : chefs) {
            if (!chef.done()) {
                anyAlive = true;
                break;
            }
        }
        if (anyAlive) {
            finish("Deadlock: " + describeDeadlock(chefs));
        } else {
            finish(null);
        }
        return false;
    }

    public boolean stepRound() {
        if (finished) {
            return false;
        }
        if (++ticks > MAX_TICKS) {
            finish("Simulation exceeded " + MAX_TICKS + " ticks");
            return false;
        }
        boolean anyAlive = false;
        boolean anyProgress = false;

        for (ChefState chef : chefs) {
            if (chef.done()) {
                continue;
            }
            anyAlive = true;
            if (step(chef)) {
                anyProgress = true;
            }
        }

        if (!anyAlive) {
            finish(null);
            return false;
        }
        if (!anyProgress) {
            finish("Deadlock: " + describeDeadlock(chefs));
            return false;
        }
        return true;
    }

    private void finish(String err) {
        this.finished = true;
        this.error = err;
    }

    private boolean step(ChefState chef) {
        String poolName = chef.program.executorName();
        if (poolName != null && !chef.executorSlotAcquired) {
            int cap = executorPoolSize.getOrDefault(poolName, Integer.MAX_VALUE);
            int active = activeExecutorSlots.getOrDefault(poolName, 0);
            if (active >= cap) {
                return false;
            }
            chef.executorSlotAcquired = true;
            activeExecutorSlots.put(poolName, active + 1);
            String capLabel = cap == Integer.MAX_VALUE ? "∞" : String.valueOf(cap);
            log(chef.name() + " acquires a slot in " + poolName
                + " (" + (active + 1) + "/" + capLabel + ")");
        }

        Instruction next = chef.peek();
        if (next instanceof Instruction.Lock l) {
            String holder = lockOwner.get(l.lockName());
            if (holder != null && !holder.equals(chef.name())) {
                return false;
            }
            lockOwner.put(l.lockName(), chef.name());
            chef.recordHeld(l.lockName());
            recordEvent(chef, l, "acquires lock '" + l.lockName() + "'");
            advanceAndMaybeRelease(chef);
            return true;
        }

        if (next instanceof Instruction.Unlock u) {
            if (!chef.releases(u.lockName())) {
                throw new SimulationException(
                    chef.name() + " tried to unlock '" + u.lockName() + "' but does not hold it"
                );
            }
            lockOwner.remove(u.lockName());
            recordEvent(chef, u, "releases lock '" + u.lockName() + "'");
            advanceAndMaybeRelease(chef);
            return true;
        }

        if (next instanceof Instruction.Read r) {
            int value = globals.getOrDefault(r.globalName(), 0);
            chef.setLocal(r.localName(), value);
            recordEvent(chef, r, "reads " + r.globalName() + " = " + value);
            advanceAndMaybeRelease(chef);
            return true;
        }

        if (next instanceof Instruction.Write w) {
            int value = evaluate(chef, w.value());
            globals.put(w.globalName(), value);
            recordEvent(chef, w, "writes " + w.globalName() + " = " + value);
            advanceAndMaybeRelease(chef);
            return true;
        }

        if (next instanceof Instruction.AtomicInc a) {
            int value = globals.getOrDefault(a.globalName(), 0) + 1;
            globals.put(a.globalName(), value);
            recordEvent(chef, a, "atomically increments " + a.globalName() + " to " + value);
            advanceAndMaybeRelease(chef);
            return true;
        }

        if (next instanceof Instruction.AtomicAdd a) {
            int delta = evaluate(chef, a.delta());
            int value = globals.getOrDefault(a.globalName(), 0) + delta;
            globals.put(a.globalName(), value);
            recordEvent(chef, a, "atomically adds " + delta + " to " + a.globalName() + " (now " + value + ")");
            advanceAndMaybeRelease(chef);
            return true;
        }

        if (next instanceof Instruction.AtomicCAS c) {
            int expected = evaluate(chef, c.expected());
            int current = globals.getOrDefault(c.globalName(), 0);
            if (current == expected) {
                int newVal = evaluate(chef, c.newValue());
                globals.put(c.globalName(), newVal);
                recordEvent(chef, c, "CAS " + c.globalName() + ": " + expected + " → " + newVal + " (success)");
            } else {
                recordEvent(chef, c, "CAS " + c.globalName() + ": expected " + expected + ", saw " + current + " (failure)");
            }
            advanceAndMaybeRelease(chef);
            return true;
        }

        if (next instanceof Instruction.Log lg) {
            chef.lastEventDetail = "logs: " + lg.message();
            this.lastStepLine = lg.line();
            log(chef.name() + ": " + lg.message());
            advanceAndMaybeRelease(chef);
            return true;
        }

        if (next instanceof Instruction.LocalSet ls) {
            int value = evaluate(chef, ls.value());
            chef.setLocal(ls.localName(), value);
            recordEvent(chef, ls, "sets " + ls.localName() + " = " + value);
            advanceAndMaybeRelease(chef);
            return true;
        }

        if (next instanceof Instruction.QueuePut put) {
            Deque<Integer> q = queues.get(put.queueName());
            if (q == null) {
                throw new SimulationException("queue '" + put.queueName() + "' is not declared");
            }
            int cap = queueCapacity.getOrDefault(put.queueName(), Integer.MAX_VALUE);
            if (q.size() >= cap) {
                return false;
            }
            int value = evaluate(chef, put.value());
            q.addLast(value);
            recordEvent(chef, put, "puts " + value + " into " + put.queueName()
                + " (size " + q.size() + "/" + cap + ")");
            advanceAndMaybeRelease(chef);
            return true;
        }

        if (next instanceof Instruction.QueueTake take) {
            Deque<Integer> q = queues.get(take.queueName());
            if (q == null) {
                throw new SimulationException("queue '" + take.queueName() + "' is not declared");
            }
            if (q.isEmpty()) {
                return false;
            }
            int value = q.removeFirst();
            chef.setLocal(take.localName(), value);
            int cap = queueCapacity.getOrDefault(take.queueName(), Integer.MAX_VALUE);
            recordEvent(chef, take, "takes " + value + " from " + take.queueName()
                + " (size " + q.size() + "/" + cap + ")");
            advanceAndMaybeRelease(chef);
            return true;
        }

        throw new SimulationException("Unknown instruction: " + next);
    }

    private void recordEvent(ChefState chef, Instruction instr, String detail) {
        chef.lastEventDetail = detail;
        this.lastStepLine = instr.line();
        log(chef.name() + " " + detail);
    }

    private void advanceAndMaybeRelease(ChefState chef) {
        chef.advance();
        String poolName = chef.program.executorName();
        if (chef.done() && chef.executorSlotAcquired && poolName != null) {
            chef.executorSlotAcquired = false;
            int active = activeExecutorSlots.getOrDefault(poolName, 0) - 1;
            activeExecutorSlots.put(poolName, active);
            int cap = executorPoolSize.getOrDefault(poolName, Integer.MAX_VALUE);
            String capLabel = cap == Integer.MAX_VALUE ? "∞" : String.valueOf(cap);
            log(chef.name() + " releases its slot in " + poolName
                + " (" + active + "/" + capLabel + ")");
        }
    }

    private int evaluate(ChefState chef, Expression expr) {
        if (expr instanceof Expression.Literal lit) {
            return lit.value();
        }
        if (expr instanceof Expression.Var var) {
            Integer value = chef.getLocal(var.name());
            if (value != null) {
                return value;
            }
            Integer global = globals.get(var.name());
            if (global != null) {
                return global;
            }
            throw new SimulationException(chef.name() + " referenced undefined name '" + var.name() + "'");
        }
        if (expr instanceof Expression.BinOp bin) {
            int l = evaluate(chef, bin.left());
            int r = evaluate(chef, bin.right());
            return switch (bin.op()) {
                case "+" -> l + r;
                case "-" -> l - r;
                case "*" -> l * r;
                case "/" -> l / r;
                default -> throw new SimulationException("Unknown operator: " + bin.op());
            };
        }
        throw new SimulationException("Unknown expression: " + expr);
    }

    private String describeDeadlock(List<ChefState> chefs) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (ChefState chef : chefs) {
            if (chef.done()) {
                continue;
            }
            Instruction next = chef.peek();
            String reason = null;
            if (next instanceof Instruction.Lock l) {
                String holder = lockOwner.get(l.lockName());
                reason = "waits on lock '" + l.lockName() + "' held by "
                    + (holder == null ? "?" : holder);
            } else if (next instanceof Instruction.QueuePut put) {
                reason = "waits to put into '" + put.queueName() + "' (full)";
            } else if (next instanceof Instruction.QueueTake take) {
                reason = "waits to take from '" + take.queueName() + "' (empty)";
            }
            if (reason != null) {
                if (!first) sb.append("; ");
                sb.append(chef.name()).append(" ").append(reason);
                first = false;
            }
        }
        return sb.toString();
    }

    private void log(String message) {
        events.add(message);
    }

    private static final class ChefState {
        private final ChefProgram program;
        private final Map<String, Integer> locals = new HashMap<>();
        private final Set<String> heldLocks = new HashSet<>();
        private int pc;
        private String lastEventDetail;
        private boolean executorSlotAcquired;

        ChefState(ChefProgram program) {
            this.program = program;
        }

        String name() {
            return program.name();
        }

        boolean done() {
            return pc >= program.instructions().size();
        }

        Instruction peek() {
            return program.instructions().get(pc);
        }

        void advance() {
            pc++;
        }

        void setLocal(String name, int value) {
            locals.put(name, value);
        }

        Integer getLocal(String name) {
            return locals.get(name);
        }

        void recordHeld(String lock) {
            heldLocks.add(lock);
        }

        boolean releases(String lock) {
            return heldLocks.remove(lock);
        }
    }
}
