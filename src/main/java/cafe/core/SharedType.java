package cafe.core;

public sealed interface SharedType {

    String javaTypeName();

    String javaInitializer();

    String description();

    record IntType(int initialValue) implements SharedType {

        @Override
        public String javaTypeName() {
            return "int";
        }

        @Override
        public String javaInitializer() {
            return String.valueOf(initialValue);
        }

        @Override
        public String description() {
            return "an int";
        }
    }

    record AtomicIntegerType(int initialValue) implements SharedType {

        @Override
        public String javaTypeName() {
            return "AtomicInteger";
        }

        @Override
        public String javaInitializer() {
            return "new AtomicInteger(" + initialValue + ")";
        }

        @Override
        public String description() {
            return "an AtomicInteger";
        }
    }

    record MonitorType() implements SharedType {

        @Override
        public String javaTypeName() {
            return "Object";
        }

        @Override
        public String javaInitializer() {
            return "new Object()";
        }

        @Override
        public String description() {
            return "an Object monitor";
        }
    }

    record LockType() implements SharedType {

        @Override
        public String javaTypeName() {
            return "ReentrantLock";
        }

        @Override
        public String javaInitializer() {
            return "new ReentrantLock()";
        }

        @Override
        public String description() {
            return "a ReentrantLock";
        }
    }
}
