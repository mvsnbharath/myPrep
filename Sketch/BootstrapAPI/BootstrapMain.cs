namespace Sketch.BootstrapAPI;

public class BootstrapMain
{
    public static async Task RunAsync()
    {
        var bootstrapService = new BootstrapService(
            new UserService(),
            new PaymentService(),
            new AddressService(),
            maxRetries: 3);

        Console.WriteLine("=== Bootstrap API Demo ===\n");

        var response = await bootstrapService.GetBootstrapAsync("user_1");

        Console.WriteLine();
        if (response != null)
        {
            Console.WriteLine("Bootstrap Response:");
            response.Print();
        }
        else
        {
            Console.WriteLine("Bootstrap failed — could not resolve user.");
        }
    }
}
