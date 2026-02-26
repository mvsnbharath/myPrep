using Sketch.BootstrapAPI;
using Sketch.DasherPayout;

namespace Sketch;

internal class Program
{
    public static async Task Main(string[] args)
    {
        decimal basePay = 0.50m;

        // ── Part 1: Basic Time-Based Payment ──
        Console.WriteLine("=== Part 1: Basic Time-Based Payment ===");
        var part1 = new Part1_BasicTimePay();
        var activities1 = new List<OrderActivity>
        {
            new() { Timestamp = "10:00", OrderId = "A", EventType = "accepted" },
            new() { Timestamp = "10:10", OrderId = "B", EventType = "accepted" },
            new() { Timestamp = "10:20", OrderId = "A", EventType = "fulfilled" },
            new() { Timestamp = "10:30", OrderId = "B", EventType = "fulfilled" },
        };
        decimal payout1 = part1.CalculatePayout(activities1, basePay);
        Console.WriteLine($"final pay: {payout1:C}\n");

        // ── Part 2: At-Store Pausing ──
        Console.WriteLine("=== Part 2: At-Store Pausing ===");
        var part2 = new Part2_AtStorePause();
        var activities2 = new List<OrderActivity>
        {
            new() { Timestamp = "10:00", OrderId = "A", EventType = "accepted" },
            new() { Timestamp = "10:10", OrderId = "B", EventType = "accepted" },
            new() { Timestamp = "10:15", OrderId = "A", EventType = "arrived_at_pickup" },
            new() { Timestamp = "10:20", OrderId = "A", EventType = "picked_up" },
            new() { Timestamp = "10:30", OrderId = "B", EventType = "arrived_at_pickup" },
            new() { Timestamp = "10:35", OrderId = "B", EventType = "picked_up" },
            new() { Timestamp = "10:40", OrderId = "A", EventType = "fulfilled" },
            new() { Timestamp = "10:50", OrderId = "B", EventType = "fulfilled" },
        };
        decimal payout2 = part2.CalculatePayout(activities2, basePay);
        Console.WriteLine($"final pay: {payout2:C}\n");

        // ── Part 3: Peak Hour Multiplier ──
        Console.WriteLine("=== Part 3: Peak Hour Multiplier ===");
        var part3 = new Part3_PeakHourMultiplier();
        var activities3 = new List<OrderActivity>
        {
            new() { Timestamp = "10:00", OrderId = "A", EventType = "accepted" },
            new() { Timestamp = "10:10", OrderId = "B", EventType = "accepted" },
            new() { Timestamp = "10:20", OrderId = "A", EventType = "picked_up" },
            new() { Timestamp = "10:30", OrderId = "B", EventType = "picked_up" },
            new() { Timestamp = "10:40", OrderId = "A", EventType = "fulfilled" },
            new() { Timestamp = "10:50", OrderId = "B", EventType = "fulfilled" },
        };
        var peakWindows = new List<(TimeSpan Start, TimeSpan End)>
        {
            (TimeSpan.Parse("10:15"), TimeSpan.Parse("10:30"))
        };
        decimal payout3 = part3.CalculatePayout(activities3, basePay, peakWindows);
        Console.WriteLine($"final pay: {payout3:C}");

        Console.WriteLine("\n");

        // ── Bootstrap API ──
        await BootstrapMain.RunAsync();
    }
}
