namespace Sketch.DasherPayout;

/// <summary>
/// Part 2 — At-Store Pausing
/// When a dasher arrives at a pickup location, that order is paused (does not count
/// toward multi-order pay) until picked up.
/// </summary>
class Part2_AtStorePause
{
    public decimal CalculatePayout(List<OrderActivity> orderActivities, decimal basePayPerMinute)
    {
        var sorted = orderActivities.OrderBy(a => TimeSpan.Parse(a.Timestamp)).ToList();

        HashSet<string> activeOrders = new();
        HashSet<string> atStoreOrders = new();
        decimal totalPay = 0m;

        for (int i = 0; i < sorted.Count; i++)
        {
            var activity = sorted[i];

            if (i > 0)
            {
                var prevTime = TimeSpan.Parse(sorted[i - 1].Timestamp);
                var currTime = TimeSpan.Parse(activity.Timestamp);
                int minutes = (int)(currTime - prevTime).TotalMinutes;

                // Orders at store don't count toward multi-order pay
                int ongoingCount = activeOrders.Count - atStoreOrders.Count;

                decimal intervalPay = ongoingCount * basePayPerMinute * minutes;
                totalPay += intervalPay;

                string atStore = atStoreOrders.Count > 0
                    ? $" (at store: {string.Join(", ", atStoreOrders)})"
                    : "";
                Console.WriteLine($"{sorted[i - 1].Timestamp} - {activity.Timestamp}: " +
                    $"{ongoingCount} active order(s){atStore}, {minutes} min, pay = {intervalPay:C}");
            }

            // Update state
            if (activity.EventType == "accepted")
                activeOrders.Add(activity.OrderId);
            else if (activity.EventType == "arrived_at_pickup")
                atStoreOrders.Add(activity.OrderId);
            else if (activity.EventType == "picked_up")
                atStoreOrders.Remove(activity.OrderId);
            else if (activity.EventType == "fulfilled")
                activeOrders.Remove(activity.OrderId);
        }

        return totalPay;
    }
}
