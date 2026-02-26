namespace Sketch.BootstrapAPI;

/// <summary>
/// Mock service that returns card details for a customerId.
/// Simulates latency and random 500 errors.
/// </summary>
public class PaymentService
{
    private static readonly Random _random = new();

    private static readonly Dictionary<string, CardDetails> _store = new()
    {
        ["cust_12345"] = new CardDetails
        {
            LastName = "Smith",
            FirstName = "John",
            CardLastFour = "4242"
        },
        ["cust_67890"] = new CardDetails
        {
            LastName = "Doe",
            FirstName = "Jane",
            CardLastFour = "1234"
        },
    };

    public async Task<PaymentResponse> GetResponseAsync(PaymentRequest request)
    {
        await Task.Delay(_random.Next(100, 800)); // simulate latency

        if (_random.Next(5) == 0)
            return new PaymentResponse { StatusCode = 500 };

        if (_store.TryGetValue(request.CustomerId, out var card))
            return new PaymentResponse { StatusCode = 200, DefaultCard = card };

        return new PaymentResponse { StatusCode = 404 };
    }
}
