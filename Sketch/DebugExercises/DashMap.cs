namespace Sketch.DebugExercises;

/// <summary>
/// A custom hash map using separate chaining for collision resolution.
/// </summary>
// Contains 4 bugs — use the failing tests to find and fix them!
public class DashMap<TKey, TValue> where TKey : notnull
{
    private class Entry
    {
        public TKey Key;
        public TValue Value;
        public Entry? Next;

        public Entry(TKey key, TValue value)
        {
            Key = key;
            Value = value;
        }
    }

    private Entry?[] _buckets;
    private int _count;
    private int _capacity;
    private const double LoadFactor = 0.75;

    public int Count => _count;

    public DashMap(int initialCapacity = 16)
    {
        _capacity = initialCapacity;
        _buckets = new Entry?[_capacity];
        _count = 0;
    }

    private int GetBucketIndex(TKey key)
    {
        return Math.Abs(key.GetHashCode()) % _capacity;
    }

    public void Put(TKey key, TValue value)
    {
        if ((double)_count / _capacity >= LoadFactor)
            Resize();

        int index = GetBucketIndex(key);
        var newEntry = new Entry(key, value);
        newEntry.Next = _buckets[index];
        _buckets[index] = newEntry;
        _count++;
    }

    public TValue Get(TKey key)
    {
        int index = GetBucketIndex(key);
        var entry = _buckets[index];

        if (entry != null && entry.Key.Equals(key))
            return entry.Value;

        throw new KeyNotFoundException($"Key '{key}' not found.");
    }

    public bool ContainsKey(TKey key)
    {
        int index = GetBucketIndex(key);
        var entry = _buckets[index];

        while (entry != null)
        {
            if (entry.Key.Equals(key))
                return true;
            entry = entry.Next;
        }
        return false;
    }

    public bool Remove(TKey key)
    {
        int index = GetBucketIndex(key);
        Entry? prev = null;
        var current = _buckets[index];

        while (current != null)
        {
            if (current.Key.Equals(key))
            {
                if (prev == null)
                    _buckets[index] = current.Next;
                else
                    prev.Next = current.Next;
                return true;
            }
            prev = current;
            current = current.Next;
        }
        return false;
    }

    private void Resize()
    {
        int newCapacity = _capacity * 2;
        var newBuckets = new Entry?[newCapacity];

        for (int i = 0; i < _capacity; i++)
        {
            newBuckets[i] = _buckets[i];
        }

        _capacity = newCapacity;
        _buckets = newBuckets;
    }

    public List<TKey> Keys()
    {
        var keys = new List<TKey>();
        for (int i = 0; i < _capacity; i++)
        {
            var entry = _buckets[i];
            while (entry != null)
            {
                keys.Add(entry.Key);
                entry = entry.Next;
            }
        }
        return keys;
    }
}
