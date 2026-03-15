namespace Sketch.DebugExercises;

/// <summary>
/// Simple in-memory cache with TTL-based expiration and size-based eviction (FIFO).
/// </summary>
// Contains 2 bugs — use the failing tests to find and fix them!
public class InMemoryCache<TKey, TValue>
    where TKey : notnull
    where TValue : class
{
    private class CacheEntry
    {
        public TValue Value { get; set; } = default!;
        public DateTime ExpiresAt { get; set; }
    }

    private readonly Dictionary<TKey, CacheEntry> _store = new();
    private readonly Queue<TKey> _evictionOrder = new();
    private readonly int _maxSize;
    private readonly TimeSpan _defaultTtl;

    public int Count => _store.Count;

    public InMemoryCache(int maxSize, TimeSpan defaultTtl)
    {
        _maxSize = maxSize;
        _defaultTtl = defaultTtl;
    }

    public void Set(TKey key, TValue value, TimeSpan? ttl = null)
    {
        if (_store.Count >= _maxSize && !_store.ContainsKey(key))
        {
            Evict();
        }

        var expiry = DateTime.UtcNow + (ttl ?? _defaultTtl);
        _store[key] = new CacheEntry { Value = value, ExpiresAt = expiry };
        _evictionOrder.Enqueue(key);
    }

    public TValue? Get(TKey key)
    {
        if (!_store.TryGetValue(key, out var entry))
        {
            return null;
        }

        if (IsExpired(entry))
        {
            _store.Remove(key);
            return null;
        }

        return entry.Value;
    }

    public bool Remove(TKey key)
    {
        return _store.Remove(key);
    }

    private bool IsExpired(CacheEntry entry)
    {
        return entry.ExpiresAt < DateTime.UtcNow;
    }

    private void Evict()
    {
        if (_evictionOrder.Count > 0)
        {
            TKey key = _evictionOrder.Dequeue();
            _store.Remove(key);
        }
    }
}