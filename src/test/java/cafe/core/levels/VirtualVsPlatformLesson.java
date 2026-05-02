package cafe.core.levels;

import java.util.concurrent.ArrayBlockingQueue;

public class VirtualVsPlatformLesson {
    static int totalReceived = 0;
    static final ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);

    public static void main(String[] args) throws InterruptedException {
        // The platform thread pool is size 1. Both chefs start as platform threads.
        // Producer takes the slot, fills the queue, then blocks on full. Consumer
        // can't start (no slot). The whole kitchen freezes.
        //
        // Hint: Java 21 has thread bodies that don't compete for OS threads.

        Thread producer = Thread.ofPlatform().start(() -> {
            try {
                queue.put(1);
                queue.put(2);
                queue.put(3);
                queue.put(4);
                queue.put(5);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Thread consumer = Thread.ofPlatform().start(() -> {
            try {
                int x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        producer.join();
        consumer.join();
        // The simulator waits for chefs automatically.
        // In real code you would call .join() on each Thread here.

        System.out.println("Total received: " + totalReceived);
    }
}