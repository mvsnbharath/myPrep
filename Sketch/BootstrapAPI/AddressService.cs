namespace Sketch.BootstrapAPI;

/// <summary>
/// Mock service that returns an address for a customerId.
/// Simulates latency and random 500 errors.
/// </summary>
public class AddressService
{
    private static readonly Random _random = new();

    private static readonly Dictionary<string, string> _store = new()
    {
        ["cust_12345"] = "123 Main St, San Francisco, CA 94102",
        ["cust_67890"] = "456 Oak Ave, Seattle, WA 98101",
    };

    public async Task<AddressResponse> GetResponseAsync(AddressRequest request)
    {
        await Task.Delay(_random.Next(100, 800)); // simulate latency

        if (_random.Next(5) == 0)
            return new AddressResponse { StatusCode = 500 };

        if (_store.TryGetValue(request.CustomerId, out var address))
            return new AddressResponse { StatusCode = 200, Address = address };

        return new AddressResponse { StatusCode = 404 };
    }
}
