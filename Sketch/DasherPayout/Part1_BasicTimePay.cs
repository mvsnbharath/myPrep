namespace Sketch.DasherPayout;

/// <summary>
/// Part 1 — Basic Time-Based Payment
/// Base Pay Rate: $0.50 per minute
/// Multi-Order Pay Rate: (number of ongoing deliveries) × base pay rate
/// </summary>
class Part1_BasicTimePay
{
    public decimal CalculatePayout(List<OrderActivity> orderActivities, decimal basePayPerMinute)
    {
        var sorted = orderActivities.OrderBy(a => TimeSpan.Parse(a.Timestamp)).ToList();

        HashSet<string> activeOrders = new();
        decimal totalPay = 0m;

        for (int i = 0; i < sorted.Count; i++)
        {
            var activity = sorted[i];

            if (i > 0)
            {
                var prevTime = TimeSpan.Parse(sorted[i - 1].Timestamp);
                var currTime = TimeSpan.Parse(activity.Timestamp);
                int minutes = (int)(currTime - prevTime).TotalMinutes;
                int ongoingCount = activeOrders.Count;

                decimal intervalPay = ongoingCount * basePayPerMinute * minutes;
                totalPay += intervalPay;

                Console.WriteLine($"{sorted[i - 1].Timestamp} - {activity.Timestamp}: " +
                    $"{ongoingCount} order(s), {minutes} min, pay = {intervalPay:C}");
            }

            // Update active orders
            if (activity.EventType == "accepted")
                activeOrders.Add(activity.OrderId);
            else if (activity.EventType == "fulfilled")
                activeOrders.Remove(activity.OrderId);
        }

        return totalPay;
    }
}
