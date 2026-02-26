namespace Sketch.BootstrapAPI;

// ── Base Request / Response ──

public class HttpRequest
{
    public string Endpoint { get; set; } = "";
}

public class HttpResponse
{
    public int StatusCode { get; set; }
    public bool IsSuccess => StatusCode >= 200 && StatusCode < 300;
}

// ── UserService ──

public class UserRequest : HttpRequest
{
    public string UserId { get; set; } = "";
}

public class UserResponse : HttpResponse
{
    public string CustomerId { get; set; } = "";
}

// ── PaymentService ──

public class PaymentRequest : HttpRequest
{
    public string CustomerId { get; set; } = "";
}

public class PaymentResponse : HttpResponse
{
    public CardDetails? DefaultCard { get; set; }
}

public class CardDetails
{
    public string LastName { get; set; } = "";
    public string FirstName { get; set; } = "";
    public string CardLastFour { get; set; } = "";
}

// ── AddressService ──

public class AddressRequest : HttpRequest
{
    public string CustomerId { get; set; } = "";
}

public class AddressResponse : HttpResponse
{
    public string Address { get; set; } = "";
}

// ── Bootstrap (aggregated) ──

public class BootstrapResponse
{
    public string CustomerId { get; set; } = "";
    public CardDetails? DefaultCard { get; set; }
    public string Address { get; set; } = "";

    public void Print()
    {
        Console.WriteLine("{");
        Console.WriteLine($"  \"CustomerId\": \"{CustomerId}\",");
        if (DefaultCard != null)
        {
            Console.WriteLine("  \"DefaultCard\": {");
            Console.WriteLine($"    \"last_name\": \"{DefaultCard.LastName}\",");
            Console.WriteLine($"    \"first_name\": \"{DefaultCard.FirstName}\",");
            Console.WriteLine($"    \"card_last_four\": \"{DefaultCard.CardLastFour}\"");
            Console.WriteLine("  },");
        }
        else
        {
            Console.WriteLine("  \"DefaultCard\": null,");
        }
        Console.WriteLine($"  \"Address\": \"{Address}\"");
        Console.WriteLine("}");
    }
}
