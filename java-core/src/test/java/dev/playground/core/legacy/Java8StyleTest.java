package dev.playground.core.legacy;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.playground.core.language.Circle;
import dev.playground.core.language.Rectangle;
import dev.playground.core.language.Shape;
import dev.playground.core.language.Shapes;
import dev.playground.core.language.Square;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * LEGACY EXAMPLE: the same logic written the way a Java 8 code base writes it, next to the Java 25 version,
 * and asserted equal. The point is reading old code, not writing it. Guide: §6 Legacy (the Java 8 → 25 map).
 */
class Java8StyleTest {

    // grace comes before ada at 20: only the tie-break on the customer puts ada first when sorting.
    private static final List<Order> ORDERS = List.of(
            new Order("ada", "books", 30),
            new Order("alan", "music", 15),
            new Order("grace", "books", 20),
            new Order("ada", "music", 20));

    private static final List<LegacyOrder> LEGACY_ORDERS = Collections.unmodifiableList(Arrays.asList(
            new LegacyOrder("ada", "books", 30),
            new LegacyOrder("alan", "music", 15),
            new LegacyOrder("grace", "books", 20),
            new LegacyOrder("ada", "music", 20)));

    @Test
    void aHandWrittenValueClassAndARecordBehaveTheSame() {
        assertThat(new LegacyOrder("ada", "books", 30)).isEqualTo(new LegacyOrder("ada", "books", 30));
        assertThat(new Order("ada", "books", 30)).isEqualTo(new Order("ada", "books", 30));
        assertThat(new LegacyOrder("ada", "books", 30)).hasSameHashCodeAs(new LegacyOrder("ada", "books", 30));
        assertThat(new Order("ada", "books", 30)).hasSameHashCodeAs(new Order("ada", "books", 30));
        // Same contract; only the toString format (and 45 lines) differ.
        assertThat(new Order("ada", "books", 30)).hasToString("Order[customer=ada, category=books, amount=30]");
    }

    @Test
    void anAnonymousComparatorAndComparatorComparing() {
        // Java 8 style (and before): an anonymous class. Java 8 already had lambdas, but old code keeps these.
        // `new ArrayList<LegacyOrder>` spells out what the diamond `<>` (Java 7) infers; you still meet it.
        List<LegacyOrder> legacy = new ArrayList<LegacyOrder>(LEGACY_ORDERS);
        Collections.sort(legacy, new Comparator<LegacyOrder>() {
            @Override
            public int compare(LegacyOrder a, LegacyOrder b) {
                int byAmount = Integer.compare(b.getAmount(), a.getAmount());
                return byAmount != 0 ? byAmount : a.getCustomer().compareTo(b.getCustomer());
            }
        });

        var modern = ORDERS.stream()
                .sorted(Comparator.comparingInt(Order::amount).reversed().thenComparing(Order::customer))
                .toList();

        assertThat(legacy).extracting(LegacyOrder::getCustomer).containsExactly("ada", "ada", "grace", "alan");
        assertThat(modern).extracting(Order::customer).containsExactly("ada", "ada", "grace", "alan");
        assertThat(modern).extracting(Order::amount).containsExactly(30, 20, 20, 15);
    }

    @Test
    void aLoopWithMapGetAndPutAndGroupingBy() {
        // Java 7 style: loop, look up, put back (Java 8 added merge; many code bases never switched).
        Map<String, Integer> legacy = new HashMap<String, Integer>();
        for (LegacyOrder order : LEGACY_ORDERS) {
            Integer total = legacy.get(order.getCustomer());
            if (total == null) {
                total = 0;
            }
            legacy.put(order.getCustomer(), total + order.getAmount());
        }

        var modern = ORDERS.stream().collect(groupingBy(Order::customer, summingInt(Order::amount)));

        assertThat(legacy)
                .isEqualTo(modern)
                .containsEntry("ada", 50)
                .containsEntry("alan", 15)
                .containsEntry("grace", 20);
    }

    @Test
    void anInstanceofAndCastChainAndAPatternSwitch() {
        List<Shape> shapes = List.of(new Circle(1), new Square(2), new Rectangle(2, 3));

        for (Shape shape : shapes) {
            assertThat(legacyArea(shape)).isEqualTo(Shapes.area(shape)); // Shapes.area: a pattern switch (§4.1)
        }
    }

    /**
     * Before Java 16: test, then cast, for every type, and a final {@code throw} because the compiler cannot
     * know the chain is complete. The sealed switch in {@link Shapes#area} needs neither.
     */
    private static double legacyArea(Object shape) {
        if (shape instanceof Circle) {
            Circle c = (Circle) shape;
            return Math.PI * c.radius() * c.radius();
        } else if (shape instanceof Square) {
            Square s = (Square) shape;
            return s.side() * s.side();
        } else if (shape instanceof Rectangle) {
            Rectangle r = (Rectangle) shape;
            return r.width() * r.height();
        }
        throw new IllegalArgumentException("Unknown shape: " + shape);
    }

    @Test
    void collectorsToListIsMutableButStreamToListIsNot() {
        // Same elements, different promise: Collectors.toList() (Java 8) gives an ArrayList today, though the
        // Javadoc guarantees nothing; Stream.toList() (Java 16) is unmodifiable.
        List<String> legacy = ORDERS.stream().map(Order::customer).collect(Collectors.toList());
        List<String> modern = ORDERS.stream().map(Order::customer).toList();

        assertThat(legacy).isEqualTo(modern);
        legacy.add("grace"); // old code relies on this...
        assertThatThrownBy(() -> modern.add("grace")) // ...so swapping in toList() breaks it at runtime
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void stringConcatenationAndATextBlock() {
        String legacy = "{\n" + "  \"customer\": \"ada\",\n" + "  \"amount\": 30\n" + "}\n";

        String modern = """
                {
                  "customer": "ada",
                  "amount": 30
                }
                """;

        assertThat(modern).isEqualTo(legacy);
    }

    @Test
    void anAnonymousRunnableAndALambdaOnAVirtualThread() throws InterruptedException {
        final AtomicReference<String> legacyResult = new AtomicReference<String>(); // `final`: required before Java 8
        Thread legacy = new Thread(new Runnable() {
            @Override
            public void run() {
                legacyResult.set("done");
            }
        });
        legacy.start();
        legacy.join();

        var modernResult = new AtomicReference<String>();
        Thread.ofVirtual().start(() -> modernResult.set("done")).join(); // §4.3

        assertThat(legacyResult.get()).isEqualTo(modernResult.get());
    }
}
