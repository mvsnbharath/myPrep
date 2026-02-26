namespace Sketch.BootstrapAPI;

/// <summary>
/// Orchestrates calls to UserService, PaymentService, and AddressService.
/// 
/// Call flow:
///   userId → UserService → customerId → PaymentService (concurrent)
///                                    └→ AddressService  (concurrent)
///
/// Failure handling:
///   - UserService failure: fatal — cannot proceed without customerId. Retries up to 3 times.
///   - PaymentService failure: returns null DefaultCard (graceful degradation).
///   - AddressService failure: returns empty Address (graceful degradation).
///   - All services retry up to maxRetries for transient 500 errors.
/// </summary>
public class BootstrapService
{
    private readonly UserService _userService;
    private readonly PaymentService _paymentService;
    private readonly AddressService _addressService;
    private readonly int _maxRetries;

    public BootstrapService(
        UserService userService,
        PaymentService paymentService,
        AddressService addressService,
        int maxRetries = 3)
    {
        _userService = userService;
        _paymentService = paymentService;
        _addressService = addressService;
        _maxRetries = maxRetries;
    }

    public async Task<BootstrapResponse?> GetBootstrapAsync(string userId)
    {
        // Step 1: Get customerId (critical — retries, fails if all attempts fail)
        var userResponse = await CallWithRetryAsync(
            () => _userService.GetResponseAsync(new UserRequest { UserId = userId }),
            "UserService");

        if (userResponse == null || !userResponse.IsSuccess)
        {
            Console.WriteLine($"[BootstrapService] UserService failed for userId={userId}. Cannot proceed.");
            return null;
        }

        string customerId = userResponse.CustomerId;
        Console.WriteLine($"[BootstrapService] Got customerId={customerId}");

        // Step 2: Call PaymentService and AddressService concurrently
        var paymentTask = CallWithRetryAsync(
            () => _paymentService.GetResponseAsync(new PaymentRequest { CustomerId = customerId }),
            "PaymentService");

        var addressTask = CallWithRetryAsync(
            () => _addressService.GetResponseAsync(new AddressRequest { CustomerId = customerId }),
            "AddressService");

        // Both tasks are already running concurrently — await each to get results
        var paymentResponse = await paymentTask;
        var addressResponse = await addressTask;

        // Step 3: Aggregate — use defaults for failed downstream services
        return new BootstrapResponse
        {
            CustomerId = customerId,
            DefaultCard = paymentResponse is { IsSuccess: true } ? paymentResponse.DefaultCard : null,
            Address = addressResponse is { IsSuccess: true } ? addressResponse.Address : "Address unavailable",
        };
    }

    private async Task<T?> CallWithRetryAsync<T>(Func<Task<T>> serviceCall, string serviceName)
        where T : HttpResponse
    {
        for (int attempt = 1; attempt <= _maxRetries; attempt++)
        {
            try
            {
                var response = await serviceCall();

                if (response.IsSuccess)
                {
                    Console.WriteLine($"[{serviceName}] Success on attempt {attempt}");
                    return response;
                }

                if (response.StatusCode == 500)
                {
                    Console.WriteLine($"[{serviceName}] 500 error on attempt {attempt}/{_maxRetries}");
                    if (attempt < _maxRetries)
                        await Task.Delay(100 * attempt); // simple backoff
                    continue;
                }

                // Non-retryable error (e.g. 404)
                Console.WriteLine($"[{serviceName}] Non-retryable status {response.StatusCode}");
                return response;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[{serviceName}] Exception on attempt {attempt}: {ex.Message}");
                if (attempt == _maxRetries) return null;
                await Task.Delay(100 * attempt);
            }
        }

        return null;
    }
}
