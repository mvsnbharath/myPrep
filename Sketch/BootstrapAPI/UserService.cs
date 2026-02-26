namespace Sketch.BootstrapAPI;

/// <summary>
/// Mock service that maps userId → customerId.
/// Simulates latency and random 500 errors.
/// </summary>
public class UserService
{
    private static readonly Random _random = new();

    // Mock data: userId → customerId
    private static readonly Dictionary<string, string> _store = new()
    {
        ["user_1"] = "cust_12345",
        ["user_2"] = "cust_67890",
    };

    public async Task<UserResponse> GetResponseAsync(UserRequest request)
    {
        await Task.Delay(_random.Next(100, 500)); // simulate latency

        // ~20% chance of 500
        if (_random.Next(5) == 0)
            return new UserResponse { StatusCode = 500 };

        if (_store.TryGetValue(request.UserId, out var customerId))
            return new UserResponse { StatusCode = 200, CustomerId = customerId };

        return new UserResponse { StatusCode = 404 };
    }
}
