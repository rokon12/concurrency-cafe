package cafe.core.sim;

import cafe.core.dsl.ChefProgram;
import cafe.core.dsl.Expression;
import cafe.core.dsl.Instruction;
import cafe.core.dsl.Program;

import java.util.ArrayList;
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
    private final List<String> events = new ArrayList<>();
    private final List<ChefState> chefs = new ArrayList<>();
    private int ticks;
    private int nextChefIndex;
    private boolean finished;
    private String error;

    public Simulator(Program program, Map<String, Integer> initialGlobals) {
        globals.putAll(initialGlobals);
        for (ChefProgram chef : program.chefs()) {
            chefs.add(new ChefState(chef));
        }
    }

    public boolean isFinished() {
        return finished;
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
        Instruction next = chef.peek();
        if (next instanceof Instruction.Lock l) {
            String holder = lockOwner.get(l.lockName());
            if (holder != null && !holder.equals(chef.name())) {
                return false;
            }
            lockOwner.put(l.lockName(), chef.name());
            chef.recordHeld(l.lockName());
            log(chef.name() + " acquires lock '" + l.lockName() + "'");
            chef.advance();
            return true;
        }

        if (next instanceof Instruction.Unlock u) {
            if (!chef.releases(u.lockName())) {
                throw new SimulationException(
                    chef.name() + " tried to unlock '" + u.lockName() + "' but does not hold it"
                );
            }
            lockOwner.remove(u.lockName());
            log(chef.name() + " releases lock '" + u.lockName() + "'");
            chef.advance();
            return true;
        }

        if (next instanceof Instruction.Read r) {
            int value = globals.getOrDefault(r.globalName(), 0);
            chef.setLocal(r.localName(), value);
            log(chef.name() + " reads " + r.globalName() + " = " + value);
            chef.advance();
            return true;
        }

        if (next instanceof Instruction.Write w) {
            int value = evaluate(chef, w.value());
            globals.put(w.globalName(), value);
            log(chef.name() + " writes " + w.globalName() + " = " + value);
            chef.advance();
            return true;
        }

        if (next instanceof Instruction.AtomicInc a) {
            int value = globals.getOrDefault(a.globalName(), 0) + 1;
            globals.put(a.globalName(), value);
            log(chef.name() + " atomically increments " + a.globalName() + " to " + value);
            chef.advance();
            return true;
        }

        if (next instanceof Instruction.AtomicAdd a) {
            int delta = evaluate(chef, a.delta());
            int value = globals.getOrDefault(a.globalName(), 0) + delta;
            globals.put(a.globalName(), value);
            log(chef.name() + " atomically adds " + delta + " to " + a.globalName() + " (now " + value + ")");
            chef.advance();
            return true;
        }

        if (next instanceof Instruction.AtomicCAS c) {
            int expected = evaluate(chef, c.expected());
            int current = globals.getOrDefault(c.globalName(), 0);
            if (current == expected) {
                int newVal = evaluate(chef, c.newValue());
                globals.put(c.globalName(), newVal);
                log(chef.name() + " CAS " + c.globalName() + ": " + expected + " → " + newVal + " (success)");
            } else {
                log(chef.name() + " CAS " + c.globalName() + ": expected " + expected + ", saw " + current + " (failure)");
            }
            chef.advance();
            return true;
        }

        if (next instanceof Instruction.Log lg) {
            log(chef.name() + ": " + lg.message());
            chef.advance();
            return true;
        }

        if (next instanceof Instruction.LocalSet ls) {
            int value = evaluate(chef, ls.value());
            chef.setLocal(ls.localName(), value);
            log(chef.name() + " sets " + ls.localName() + " = " + value);
            chef.advance();
            return true;
        }

        throw new SimulationException("Unknown instruction: " + next);
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
            if (next instanceof Instruction.Lock l) {
                String holder = lockOwner.get(l.lockName());
                if (!first) {
                    sb.append("; ");
                }
                sb.append(chef.name())
                    .append(" waits on '")
                    .append(l.lockName())
                    .append("' held by ")
                    .append(holder == null ? "?" : holder);
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
