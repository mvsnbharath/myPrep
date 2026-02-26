namespace Sketch.DebugExercises;

public class Node
{
    public string Id { get; }
    public string Status { get; }

    public Node(string id, string status)
    {
        Id = id;
        Status = status;
    }

    public override string ToString() => $"{Id} ({Status})";
}

/// <summary>
/// Round-robin node selector that skips unavailable nodes.
/// Maintains a global index so selection does not reset per call.
/// </summary>
// Contains 3 bugs — use the failing tests to find and fix them!
public class NodeSelector
{
    private readonly List<Node> _nodes;
    private int _currentIndex = 0;

    public NodeSelector(List<Node> nodes)
    {
        _nodes = nodes ?? throw new ArgumentNullException(nameof(nodes));
        if (nodes.Count == 0)
            throw new ArgumentException("Node list cannot be empty.", nameof(nodes));
    }

    public Node? PickNode()
    {
        int _currentIndex = 0;

        for (int i = 0; i < _nodes.Count; i++)
        {
            int index = _currentIndex % _nodes.Count;
            _currentIndex++;

            if (_nodes[index].Status == "Available")
                return _nodes[index];
        }

        return null;
    }
}
