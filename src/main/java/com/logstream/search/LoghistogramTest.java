package com.logstream.search;



import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.axlero.logstream.Logentry;



class LogHistogramTest {

    private static final Instant T0 = Instant.parse("2026-11-02T10:00:00Z");

    private static Logentry at(Instant time) {
        return new Logentry("id", time, "INFO", "auth", "m");
    }


    void emptyInputGivesNoBuckets() {
        assertTrue(Loghistogram.build(List.of(), Duration.ofMinutes(1)).isEmpty());
    }

    private void assertTrue(boolean empty) {
       
        throw new UnsupportedOperationException("Unimplemented method 'assertTrue'");
    }

    
    void logsInTheSameMinuteAreCountedTogether() {
        List<Loghistogram.Bucket> result = Loghistogram.build(
                List.of(at(T0.plusSeconds(5)), at(T0.plusSeconds(30)), at(T0.plusSeconds(59))),
                Duration.ofMinutes(1));
        assertEquals(1, result.size());
        assertEquals(T0, result.get(0).start());
        assertEquals(3, result.get(0).count());
    }

    private void assertEquals(int i, int size) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'assertEquals'");
    }


    private void assertEquals(List<Integer> i, List<Integer> size) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'assertEquals'");
    }


    private void assertEquals(Instant t02, Instant size) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'assertEquals'");
    }


   
    void bucketsStartOnRoundTimes() {
        // 10:00:45 belongs to the bucket that starts at 10:00:00
        List<Loghistogram.Bucket> result = Loghistogram.build(
                List.of(at(T0.plusSeconds(45))), Duration.ofMinutes(1));
        assertEquals(T0, result.get(0).start());
    }

   
    void emptyBucketsInTheMiddleAreFilledWithZero() {
        List<Loghistogram.Bucket> result = Loghistogram.build(
                List.of(at(T0.plusSeconds(10)), at(T0.plusSeconds(190))), // minute 0 and minute 3
                Duration.ofMinutes(1));
        assertEquals(4, result.size());
        assertEquals(List.of(1, 0, 0, 1), result.stream().map(Loghistogram.Bucket::count).toList());
    }

   
    void bucketsAreInTimeOrderEvenIfLogsAreNewestFirst() {
        List<Loghistogram.Bucket> result = Loghistogram.build(
                List.of(at(T0.plusSeconds(130)), at(T0.plusSeconds(70)), at(T0.plusSeconds(5))),
                Duration.ofMinutes(1));
        assertEquals(List.of(1, 1, 1), result.stream().map(Loghistogram.Bucket::count).toList());
        assertTrue(result.get(0).start().isBefore(result.get(2).start()));
    }

   
    void totalCountEqualsNumberOfLogs() {
        List<Logentry> logs = List.of(at(T0), at(T0.plusSeconds(61)), at(T0.plusSeconds(62)),
                at(T0.plusSeconds(400)));
        int sum = Loghistogram.build(logs, Duration.ofMinutes(1)).stream()
                .mapToInt(Loghistogram.Bucket::count).sum();
        assertEquals(4, sum);
    }

  
    void zeroOrNegativeBucketSizeIsRejected() {
        List<Logentry> logs = List.of(at(T0));
        assertThrows1(IllegalArgumentException.class, () -> Loghistogram.build(logs, Duration.ZERO));
        assertThrows1(IllegalArgumentException.class, () -> Loghistogram.build(logs, Duration.ofMinutes(-1)));
    }

    
    private void assertThrows1(Class<IllegalArgumentException> class1, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            return;
        }
        throw new AssertionError("Expected " + class1.getSimpleName());
    }


    private void assertThrows(Class<IllegalArgumentException> class1, Object object) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'assertThrows'");
    }


    private void assertThrows1(Class<IllegalArgumentException> class1, Object object) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'assertThrows'");
    }


    void tooManyBucketsIsRejected() {
        List<Logentry> logs = List.of(at(T0), at(T0.plus(Duration.ofDays(30))));
        assertThrows1(IllegalArgumentException.class, () -> Loghistogram.build(logs, Duration.ofSeconds(1)));
    }
}