package cafe.core.dsl;

import cafe.core.SharedType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Parser {

    private final List<Token> tokens;
    private final Map<String, SharedType> declarations;
    private int pos;

    private final Set<String> locals = new HashSet<>();
    private int tempCounter;
    private int virtualChefCounter;
    private int platformChefCounter;

    private Parser(List<Token> tokens, Map<String, SharedType> declarations) {
        this.tokens = tokens;
        this.declarations = declarations;
    }

    public static Program parse(String source, Map<String, SharedType> declarations) {
        List<Token> tokens = new Tokenizer().tokenize(source);
        return new Parser(tokens, declarations).parseProgram();
    }

    private Program parseProgram() {
        List<ChefProgram> chefs = new ArrayList<>();
        while (pos < tokens.size()) {
            chefs.add(parseChefDecl());
        }
        if (chefs.isEmpty()) {
            throw new ParseException(1, 1,
                "expected a chef declaration like 'Thread.ofVirtual().start(() -> { ... });'");
        }
        return new Program(chefs);
    }

    private ChefProgram parseChefDecl() {
        Token first = peek();
        String chefName = null;

        if (isWord(first, "Thread") || isWord(first, "var")) {
            int save = pos;
            advance();
            if (pos < tokens.size() && peek().kind() == TokenKind.WORD
                && !isWord(peek(), "ofVirtual") && !isWord(peek(), "ofPlatform")
                && !isSymbol(peek(), ".")) {
                Token nameTok = peek();
                advance();
                if (pos < tokens.size() && isSymbol(peek(), "=")) {
                    advance();
                    chefName = nameTok.text();
                } else {
                    pos = save;
                }
            } else {
                pos = save;
            }
        }

        Token threadTok = peek();
        if (!isWord(threadTok, "Thread")) {
            throw unexpected(threadTok, "'Thread'");
        }
        advance();
        expectSymbol(".");

        Token kindTok = peek();
        boolean virtual;
        if (isWord(kindTok, "ofVirtual")) {
            virtual = true;
        } else if (isWord(kindTok, "ofPlatform")) {
            virtual = false;
        } else {
            throw unexpected(kindTok, "'ofVirtual' or 'ofPlatform'");
        }
        advance();

        expectSymbol("(");
        expectSymbol(")");
        expectSymbol(".");
        expectWord("start");
        expectSymbol("(");

        if (chefName == null) {
            chefName = virtual
                ? "vthread-" + (++virtualChefCounter)
                : "thread-" + (++platformChefCounter);
        }

        locals.clear();
        tempCounter = 0;

        expectSymbol("(");
        expectSymbol(")");
        expectSymbol("->");
        List<Instruction> body = parseBlock();

        expectSymbol(")");
        expectSymbol(";");

        return new ChefProgram(chefName, body);
    }

    private List<Instruction> parseBlock() {
        expectSymbol("{");
        List<Instruction> body = new ArrayList<>();
        while (pos < tokens.size() && !isSymbol(peek(), "}")) {
            parseStatement(body);
        }
        expectSymbol("}");
        return body;
    }

    private void parseStatement(List<Instruction> out) {
        Token first = peek();

        if (isWord(first, "synchronized")) {
            parseSynchronized(out);
            return;
        }

        if (isWord(first, "int")) {
            parseLocalDecl(out);
            return;
        }

        if (isWord(first, "System")) {
            parsePrintln(out);
            return;
        }

        if (isSymbol(first, "++") || isSymbol(first, "--")) {
            parsePrefixIncrement(out);
            return;
        }

        if (first.kind() == TokenKind.WORD) {
            int save = pos;
            advance();
            Token next = peek();
            if (isSymbol(next, "=")) {
                pos = save;
                parseAssignment(out);
                return;
            }
            if (isSymbol(next, "++") || isSymbol(next, "--")) {
                pos = save;
                parsePostfixIncrement(out);
                return;
            }
            if (isSymbol(next, "+=") || isSymbol(next, "-=")) {
                pos = save;
                parseCompoundAssign(out);
                return;
            }
            if (isSymbol(next, ".")) {
                pos = save;
                parseMethodCallStatement(out);
                return;
            }
            pos = save;
        }

        throw unexpected(first, "a statement");
    }

    private void parseSynchronized(List<Instruction> out) {
        Token syncTok = peek();
        advance();
        expectSymbol("(");
        Token nameTok = expectWord("a lock name");
        String lockName = nameTok.text();
        SharedType type = lookup(lockName, nameTok);
        if (!(type instanceof SharedType.MonitorType)) {
            throw error(nameTok, "'" + lockName + "' is " + type.description()
                + " — synchronized(...) requires an Object monitor"
                + suggestForMonitor(type));
        }
        expectSymbol(")");
        out.add(new Instruction.Lock(lockName, syncTok.line()));
        List<Instruction> body = parseBlock();
        out.addAll(body);
        out.add(new Instruction.Unlock(lockName, syncTok.line()));
    }

    private void parseLocalDecl(List<Instruction> out) {
        Token intTok = peek();
        advance();
        Token nameTok = expectWord("a local variable name");
        String name = nameTok.text();
        if (declarations.containsKey(name)) {
            throw error(nameTok, "'" + name + "' is a shared variable; choose a different local name");
        }
        expectSymbol("=");
        Expression expr = parseExpr();
        expectSymbol(";");

        if (expr instanceof Expression.Var v && declarations.containsKey(v.name())) {
            requireReadable(v.name(), nameTok);
            out.add(new Instruction.Read(name, v.name(), intTok.line()));
        } else if (expr instanceof Expression.AtomicGet ag) {
            out.add(new Instruction.Read(name, ag.name(), intTok.line()));
        } else {
            int line = intTok.line();
            Expression rewritten = liftGlobalReads(expr, out, line);
            out.add(new Instruction.LocalSet(name, rewritten, line));
        }
        locals.add(name);
    }

    private void parseAssignment(List<Instruction> out) {
        Token nameTok = peek();
        advance();
        Token eq = peek();
        advance();
        Expression expr = parseExpr();
        expectSymbol(";");

        String name = nameTok.text();
        int line = eq.line();

        if (declarations.containsKey(name)) {
            SharedType type = declarations.get(name);
            if (!(type instanceof SharedType.IntType)) {
                throw error(nameTok, "'" + name + "' is " + type.description()
                    + " — direct assignment is only allowed on int"
                    + suggestForWrite(type, name));
            }
            Expression rewritten = liftGlobalReads(expr, out, line);
            out.add(new Instruction.Write(name, rewritten, line));
        } else if (locals.contains(name)) {
            Expression rewritten = liftGlobalReads(expr, out, line);
            out.add(new Instruction.LocalSet(name, rewritten, line));
        } else {
            throw error(nameTok, "unknown variable '" + name + "'");
        }
    }

    private void parsePostfixIncrement(List<Instruction> out) {
        Token nameTok = peek();
        advance();
        Token op = peek();
        advance();
        expectSymbol(";");
        emitIncrement(out, nameTok, op);
    }

    private void parsePrefixIncrement(List<Instruction> out) {
        Token op = peek();
        advance();
        Token nameTok = expectWord("a variable name");
        expectSymbol(";");
        emitIncrement(out, nameTok, op);
    }

    private void parseCompoundAssign(List<Instruction> out) {
        Token nameTok = peek();
        advance();
        Token op = peek();
        advance();
        Expression rhs = parseExpr();
        expectSymbol(";");

        String name = nameTok.text();
        String binOp = op.text().equals("+=") ? "+" : "-";

        if (declarations.containsKey(name)) {
            SharedType type = declarations.get(name);
            if (!(type instanceof SharedType.IntType)) {
                throw error(nameTok, "'" + name + "' is " + type.description()
                    + " — '" + op.text() + "' is only valid on int"
                    + suggestForWrite(type, name));
            }
            String temp = freshTemp();
            int line = op.line();
            out.add(new Instruction.Read(temp, name, line));
            Expression rewrittenRhs = liftGlobalReads(rhs, out, line);
            Expression sum = new Expression.BinOp(binOp, new Expression.Var(temp), rewrittenRhs);
            out.add(new Instruction.Write(name, sum, line));
        } else if (locals.contains(name)) {
            int line = op.line();
            Expression rewrittenRhs = liftGlobalReads(rhs, out, line);
            Expression sum = new Expression.BinOp(binOp, new Expression.Var(name), rewrittenRhs);
            out.add(new Instruction.LocalSet(name, sum, line));
        } else {
            throw error(nameTok, "unknown variable '" + name + "'");
        }
    }

    private void emitIncrement(List<Instruction> out, Token nameTok, Token op) {
        String name = nameTok.text();
        String binOp = op.text().equals("++") ? "+" : "-";
        int line = nameTok.line();

        if (declarations.containsKey(name)) {
            SharedType type = declarations.get(name);
            if (!(type instanceof SharedType.IntType)) {
                throw error(nameTok, "'" + name + "' is " + type.description()
                    + " — '" + op.text() + "' is only valid on int"
                    + suggestForWrite(type, name));
            }
            String temp = freshTemp();
            out.add(new Instruction.Read(temp, name, line));
            Expression sum = new Expression.BinOp(binOp,
                new Expression.Var(temp), new Expression.Literal(1));
            out.add(new Instruction.Write(name, sum, line));
        } else if (locals.contains(name)) {
            Expression sum = new Expression.BinOp(binOp,
                new Expression.Var(name), new Expression.Literal(1));
            out.add(new Instruction.LocalSet(name, sum, line));
        } else {
            throw error(nameTok, "unknown variable '" + name + "'");
        }
    }

    private void parseMethodCallStatement(List<Instruction> out) {
        Token targetTok = peek();
        advance();
        expectSymbol(".");
        Token methodTok = expectWord("a method name");
        expectSymbol("(");
        List<Expression> args = parseArgs();
        expectSymbol(")");
        expectSymbol(";");

        String target = targetTok.text();
        String method = methodTok.text();
        int line = targetTok.line();

        SharedType type = lookup(target, targetTok);

        switch (method) {
            case "incrementAndGet", "getAndIncrement" -> {
                requireType(type, SharedType.AtomicIntegerType.class, targetTok, target,
                    "'." + method + "()' is only valid on AtomicInteger");
                if (!args.isEmpty()) {
                    throw error(methodTok, "'" + method + "' takes no arguments");
                }
                out.add(new Instruction.AtomicInc(target, line));
            }
            case "set" -> {
                requireType(type, SharedType.AtomicIntegerType.class, targetTok, target,
                    "'.set(...)' is only valid on AtomicInteger");
                if (args.size() != 1) {
                    throw error(methodTok, "'set' takes exactly one argument");
                }
                Expression rewritten = liftGlobalReads(args.get(0), out, line);
                out.add(new Instruction.Write(target, rewritten, line));
            }
            case "addAndGet", "getAndAdd" -> {
                requireType(type, SharedType.AtomicIntegerType.class, targetTok, target,
                    "'." + method + "(...)' is only valid on AtomicInteger");
                if (args.size() != 1) {
                    throw error(methodTok, "'" + method + "' takes exactly one argument");
                }
                Expression rewritten = liftGlobalReads(args.get(0), out, line);
                out.add(new Instruction.AtomicAdd(target, rewritten, line));
            }
            case "compareAndSet" -> {
                requireType(type, SharedType.AtomicIntegerType.class, targetTok, target,
                    "'.compareAndSet(...)' is only valid on AtomicInteger");
                if (args.size() != 2) {
                    throw error(methodTok, "'compareAndSet' takes exactly two arguments (expected, new)");
                }
                Expression expected = liftGlobalReads(args.get(0), out, line);
                Expression newVal = liftGlobalReads(args.get(1), out, line);
                out.add(new Instruction.AtomicCAS(target, expected, newVal, line));
            }
            case "lock" -> {
                requireType(type, SharedType.LockType.class, targetTok, target,
                    "'.lock()' is only valid on ReentrantLock");
                if (!args.isEmpty()) {
                    throw error(methodTok, "'lock' takes no arguments");
                }
                out.add(new Instruction.Lock(target, line));
            }
            case "unlock" -> {
                requireType(type, SharedType.LockType.class, targetTok, target,
                    "'.unlock()' is only valid on ReentrantLock");
                if (!args.isEmpty()) {
                    throw error(methodTok, "'unlock' takes no arguments");
                }
                out.add(new Instruction.Unlock(target, line));
            }
            default -> throw error(methodTok,
                "unsupported method '" + method + "' on " + type.description()
                    + " (try incrementAndGet, addAndGet, compareAndSet, set, lock, or unlock)");
        }
    }

    private void parsePrintln(List<Instruction> out) {
        Token sysTok = peek();
        advance();
        expectSymbol(".");
        expectWord("'out'", "out");
        expectSymbol(".");
        expectWord("'println'", "println");
        expectSymbol("(");
        Token msgTok = peek();
        if (msgTok.kind() != TokenKind.STRING) {
            throw unexpected(msgTok, "a string literal inside println");
        }
        advance();
        expectSymbol(")");
        expectSymbol(";");
        out.add(new Instruction.Log(msgTok.text(), sysTok.line()));
    }

    private List<Expression> parseArgs() {
        List<Expression> args = new ArrayList<>();
        if (isSymbol(peek(), ")")) {
            return args;
        }
        args.add(parseExpr());
        while (isSymbol(peek(), ",")) {
            advance();
            args.add(parseExpr());
        }
        return args;
    }

    private Expression parseExpr() {
        return parseAdd();
    }

    private Expression parseAdd() {
        Expression left = parseMul();
        while (isSymbol(peek(), "+") || isSymbol(peek(), "-")) {
            Token op = peek();
            advance();
            Expression right = parseMul();
            left = new Expression.BinOp(op.text(), left, right);
        }
        return left;
    }

    private Expression parseMul() {
        Expression left = parseAtom();
        while (isSymbol(peek(), "*") || isSymbol(peek(), "/")) {
            Token op = peek();
            advance();
            Expression right = parseAtom();
            left = new Expression.BinOp(op.text(), left, right);
        }
        return left;
    }

    private Expression parseAtom() {
        Token tok = peek();
        if (tok.kind() == TokenKind.NUMBER) {
            advance();
            return new Expression.Literal(Integer.parseInt(tok.text()));
        }
        if (tok.kind() == TokenKind.WORD) {
            advance();
            if (pos < tokens.size() && isSymbol(peek(), ".")) {
                advance();
                Token methodTok = expectWord("a method name");
                expectSymbol("(");
                expectSymbol(")");
                String name = tok.text();
                String method = methodTok.text();
                if (!method.equals("get")) {
                    throw error(methodTok,
                        "only '.get()' is supported inside expressions; got '." + method + "()'");
                }
                SharedType type = declarations.get(name);
                if (type == null) {
                    throw error(tok, "'" + name + "' is not a known shared variable");
                }
                if (!(type instanceof SharedType.AtomicIntegerType)) {
                    throw error(tok, "'" + name + "' is " + type.description()
                        + " — '.get()' is only valid on AtomicInteger");
                }
                return new Expression.AtomicGet(name);
            }
            return new Expression.Var(tok.text());
        }
        if (isSymbol(tok, "(")) {
            advance();
            Expression inner = parseExpr();
            expectSymbol(")");
            return inner;
        }
        throw unexpected(tok, "a number, variable, or '('");
    }

    private Expression liftGlobalReads(Expression expr, List<Instruction> out, int line) {
        if (expr instanceof Expression.Var v) {
            if (declarations.containsKey(v.name())) {
                requireReadable(v.name(), null);
                String temp = freshTemp();
                out.add(new Instruction.Read(temp, v.name(), line));
                return new Expression.Var(temp);
            }
            return v;
        }
        if (expr instanceof Expression.AtomicGet ag) {
            String temp = freshTemp();
            out.add(new Instruction.Read(temp, ag.name(), line));
            return new Expression.Var(temp);
        }
        if (expr instanceof Expression.BinOp bin) {
            Expression left = liftGlobalReads(bin.left(), out, line);
            Expression right = liftGlobalReads(bin.right(), out, line);
            return new Expression.BinOp(bin.op(), left, right);
        }
        return expr;
    }

    private void requireReadable(String name, Token tok) {
        SharedType type = declarations.get(name);
        if (type instanceof SharedType.IntType) {
            return;
        }
        Token reportAt = tok != null ? tok : peek();
        throw error(reportAt, "'" + name + "' is " + type.description()
            + " — it cannot be read as an int directly"
            + suggestForRead(type, name));
    }

    private String freshTemp() {
        String name = "_t" + (tempCounter++);
        locals.add(name);
        return name;
    }

    private SharedType lookup(String name, Token tok) {
        SharedType type = declarations.get(name);
        if (type == null) {
            throw error(tok, "'" + name + "' is not a known shared variable");
        }
        return type;
    }

    private void requireType(SharedType actual,
                             Class<? extends SharedType> expected,
                             Token tok,
                             String name,
                             String reason) {
        if (!expected.isInstance(actual)) {
            throw error(tok, "'" + name + "' is " + actual.description()
                + " — " + reason
                + suggestForMismatch(actual, name));
        }
    }

    private static String suggestForMonitor(SharedType actual) {
        if (actual instanceof SharedType.LockType) {
            return ". Use lock.lock() / lock.unlock() instead.";
        }
        return ".";
    }

    private static String suggestForWrite(SharedType actual, String name) {
        if (actual instanceof SharedType.AtomicIntegerType) {
            return ". Try '" + name + ".incrementAndGet()' or '" + name + ".set(...)'.";
        }
        return ".";
    }

    private static String suggestForRead(SharedType actual, String name) {
        if (actual instanceof SharedType.AtomicIntegerType) {
            return ". (Reading AtomicInteger via '.get()' isn't supported yet — operate on it via .incrementAndGet() or .set().)";
        }
        return ".";
    }

    private static String suggestForMismatch(SharedType actual, String name) {
        if (actual instanceof SharedType.IntType) {
            return ". Use '" + name + "++' / '" + name + " = ...' for int.";
        }
        if (actual instanceof SharedType.MonitorType) {
            return ". Use 'synchronized (" + name + ") { ... }' for an Object monitor.";
        }
        if (actual instanceof SharedType.LockType) {
            return ". Use '" + name + ".lock()' / '" + name + ".unlock()' for ReentrantLock.";
        }
        return ".";
    }

    private Token peek() {
        if (pos >= tokens.size()) {
            int line = tokens.isEmpty() ? 1 : tokens.get(tokens.size() - 1).line();
            throw new ParseException(line, 1, "unexpected end of input");
        }
        return tokens.get(pos);
    }

    private void advance() {
        pos++;
    }

    private Token expectWord(String description) {
        Token tok = peek();
        if (tok.kind() != TokenKind.WORD) {
            throw unexpected(tok, description);
        }
        advance();
        return tok;
    }

    private void expectWord(String description, String exact) {
        Token tok = peek();
        if (tok.kind() != TokenKind.WORD || !tok.text().equals(exact)) {
            throw unexpected(tok, description);
        }
        advance();
    }

    private void expectSymbol(String text) {
        Token tok = peek();
        if (!isSymbol(tok, text)) {
            throw unexpected(tok, "'" + text + "'");
        }
        advance();
    }

    private static boolean isWord(Token tok, String text) {
        return tok.kind() == TokenKind.WORD && tok.text().equals(text);
    }

    private static boolean isSymbol(Token tok, String text) {
        return tok.kind() == TokenKind.SYMBOL && tok.text().equals(text);
    }

    private static ParseException error(Token tok, String message) {
        return new ParseException(tok.line(), tok.column(), message);
    }

    private static ParseException unexpected(Token tok, String expected) {
        return new ParseException(tok.line(), tok.column(),
            "expected " + expected + " but found '" + tok.text() + "'");
    }
}
