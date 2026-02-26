namespace Sketch.DebugExercises;

public static class DebugExercisesMain
{
    private static int _passed = 0;
    private static int _failed = 0;

    public static void Run()
    {
        _passed = 0;
        _failed = 0;

        Console.WriteLine("========================================");
        Console.WriteLine("   DEBUG EXERCISES — Fix the Bugs!");
        Console.WriteLine("========================================\n");

        RunNodeSelectorTests();
        RunDashMapTests();
        RunInMemoryCacheTests();

        Console.WriteLine("=== Summary ===");
        Console.WriteLine($"  Passed: {_passed}");
        Console.WriteLine($"  Failed: {_failed}");
        Console.WriteLine($"  Total:  {_passed + _failed}");

        if (_failed == 0)
            Console.WriteLine("\n  All tests pass! All bugs fixed!");
        else
            Console.WriteLine($"\n  {_failed} test(s) failing — keep debugging!");
    }

    // ═══════════════════════════════════════
    // NodeSelector Tests (3 bugs to find)
    // ═══════════════════════════════════════
    private static void RunNodeSelectorTests()
    {
        Console.WriteLine("-- NodeSelector Tests (3 bugs) --");

        // Test 1: Should pick the available node
        {
            var selector = new NodeSelector(new List<Node>
            {
                new("node1", "unavailable"),
                new("node2", "available"),
            });
            var picked = selector.PickNode();
            AssertNotNull(() => picked, "Pick returns a non-null node");
            AssertEqual("node2", () => picked?.Id ?? "null", "Picks the available node (node2)");
        }

        // Test 2: Round-robin across available nodes
        {
            var selector = new NodeSelector(new List<Node>
            {
                new("node1", "available"),
                new("node2", "unavailable"),
                new("node3", "available"),
            });
            var first = selector.PickNode();
            var second = selector.PickNode();
            AssertEqual("node1", () => first?.Id ?? "null", "First pick is node1");
            AssertEqual("node3", () => second?.Id ?? "null", "Second pick is node3 (round-robin)");
        }

        // Test 3: All unavailable should throw InvalidOperationException
        {
            var selector = new NodeSelector(new List<Node>
            {
                new("node1", "unavailable"),
                new("node2", "unavailable"),
            });
            AssertThrows<InvalidOperationException>(
                () => selector.PickNode(),
                "All unavailable throws InvalidOperationException");
        }

        Console.WriteLine();
    }

    // ═══════════════════════════════════════
    // DashMap Tests (4 bugs to find)
    // ═══════════════════════════════════════
    private static void RunDashMapTests()
    {
        Console.WriteLine("-- DashMap Tests (4 bugs) --");

        // Test 1: Basic put and get (no collision, no resize — should pass)
        {
            var map = new DashMap<int, string>(4);
            map.Put(1, "one");
            map.Put(2, "two");
            AssertEqual("one", () => map.Get(1), "Basic get key 1");
            AssertEqual("two", () => map.Get(2), "Basic get key 2");
        }

        // Test 2: Put same key should update value, not duplicate
        {
            var map = new DashMap<int, string>(4);
            map.Put(1, "first");
            map.Put(1, "second");
            AssertEqual(1, () => map.Count, "Put same key twice -> count stays 1");
            AssertEqual("second", () => map.Get(1), "Put same key twice -> value updated");
        }

        // Test 3: Get must traverse chain on hash collision
        // int.GetHashCode() == the int itself, so key 1 and key 5 both land in bucket 1 (mod 4)
        {
            var map = new DashMap<int, string>(4);
            map.Put(1, "one");     // bucket 1%4 = 1
            map.Put(5, "five");    // bucket 5%4 = 1 (collision!)
            AssertEqual("one", () => map.Get(1), "Get key 1 after collision with key 5");
            AssertEqual("five", () => map.Get(5), "Get key 5 (head of chain)");
        }

        // Test 4: Remove should decrement count
        {
            var map = new DashMap<int, string>(4);
            map.Put(1, "one");
            map.Put(2, "two");
            map.Remove(1);
            AssertEqual(1, () => map.Count, "Count decrements after Remove");
            AssertEqual(false, () => map.ContainsKey(1), "Removed key not found");
        }

        // Test 5: Resize must rehash entries to new bucket positions
        // key 4: bucket 4%4=0 pre-resize -> 4%8=4 post-resize
        // key 5: bucket 5%4=1 pre-resize -> 5%8=5 post-resize
        {
            var map = new DashMap<int, string>(4);
            map.Put(4, "four");    // bucket 0
            map.Put(5, "five");    // bucket 1
            map.Put(6, "six");     // bucket 2, count=3
            map.Put(7, "seven");   // triggers resize (3/4 >= 0.75), then put
            AssertEqual("four", () => map.Get(4), "Get key 4 after resize (bucket 4%8=4)");
            AssertEqual("five", () => map.Get(5), "Get key 5 after resize (bucket 5%8=5)");
            AssertEqual("seven", () => map.Get(7), "Get key 7 after resize");
        }

        Console.WriteLine();
    }

    // ═══════════════════════════════════════
    // InMemoryCache Tests (2 bugs to find)
    // ═══════════════════════════════════════
    private static void RunInMemoryCacheTests()
    {
        Console.WriteLine("-- InMemoryCache Tests (2 bugs) --");

        // Test 1: Get returns value before TTL expires
        {
            var cache = new InMemoryCache<string, string>(100, TimeSpan.FromSeconds(10));
            cache.Set("key1", "value1");
            AssertEqual("value1", () => cache.Get("key1"), "Get returns value before TTL expires");
        }

        // Test 2: Get returns null after TTL expires
        {
            var cache = new InMemoryCache<string, string>(100, TimeSpan.FromMilliseconds(200));
            cache.Set("key1", "value1");
            Thread.Sleep(300);
            AssertNull(() => cache.Get("key1"), "Get returns null after TTL expires");
        }

        // Test 3: Eviction respects max size
        {
            var cache = new InMemoryCache<string, string>(2, TimeSpan.FromMinutes(10));
            cache.Set("a", "1");
            cache.Set("b", "2");
            cache.Set("c", "3"); // should evict "a" (oldest)
            AssertEqual(2, () => cache.Count, "Count respects maxSize after eviction");
        }

        Console.WriteLine();
    }

    // ═══════════════════════════════════════
    // Test Helpers
    // ═══════════════════════════════════════

    private static void AssertEqual<T>(T expected, Func<T> actualFactory, string testName)
    {
        try
        {
            var actual = actualFactory();
            if (EqualityComparer<T>.Default.Equals(expected, actual))
            {
                _passed++;
                Console.WriteLine($"  [PASS] {testName}");
            }
            else
            {
                _failed++;
                var expectedStr = expected is not null ? expected.ToString() : "null";
                var actualStr = actual is not null ? actual.ToString() : "null";
                Console.WriteLine($"  [FAIL] {testName}");
                Console.WriteLine($"         Expected: {expectedStr}");
                Console.WriteLine($"         Actual:   {actualStr}");
            }
        }
        catch (Exception ex)
        {
            _failed++;
            Console.WriteLine($"  [FAIL] {testName}");
            Console.WriteLine($"         Exception: {ex.GetType().Name}: {ex.Message}");
        }
    }

    private static void AssertNotNull<T>(Func<T?> actualFactory, string testName)
    {
        try
        {
            var actual = actualFactory();
            if (actual is not null)
            {
                _passed++;
                Console.WriteLine($"  [PASS] {testName}");
            }
            else
            {
                _failed++;
                Console.WriteLine($"  [FAIL] {testName}");
                Console.WriteLine($"         Expected: not null");
                Console.WriteLine($"         Actual:   null");
            }
        }
        catch (Exception ex)
        {
            _failed++;
            Console.WriteLine($"  [FAIL] {testName}");
            Console.WriteLine($"         Exception: {ex.GetType().Name}: {ex.Message}");
        }
    }

    private static void AssertNull<T>(Func<T?> actualFactory, string testName) where T : class
    {
        try
        {
            var actual = actualFactory();
            if (actual is null)
            {
                _passed++;
                Console.WriteLine($"  [PASS] {testName}");
            }
            else
            {
                _failed++;
                Console.WriteLine($"  [FAIL] {testName}");
                Console.WriteLine($"         Expected: null");
                Console.WriteLine($"         Actual:   {actual}");
            }
        }
        catch (Exception ex)
        {
            _failed++;
            Console.WriteLine($"  [FAIL] {testName}");
            Console.WriteLine($"         Exception: {ex.GetType().Name}: {ex.Message}");
        }
    }

    private static void AssertThrows<TException>(Action action, string testName)
        where TException : Exception
    {
        try
        {
            action();
            _failed++;
            Console.WriteLine($"  [FAIL] {testName}");
            Console.WriteLine($"         Expected: {typeof(TException).Name} to be thrown");
            Console.WriteLine($"         Actual:   No exception thrown");
        }
        catch (TException)
        {
            _passed++;
            Console.WriteLine($"  [PASS] {testName}");
        }
        catch (Exception ex)
        {
            _failed++;
            Console.WriteLine($"  [FAIL] {testName}");
            Console.WriteLine($"         Expected: {typeof(TException).Name}");
            Console.WriteLine($"         Actual:   {ex.GetType().Name}: {ex.Message}");
        }
    }
}
