namespace Sketch.DasherPayout;

class MyMain
{
    public decimal CalculatePayout(
        List<OrderActivity> orderActivities,
        decimal basePayPerMinute,
        List<(TimeSpan Start, TimeSpan End)>? peakWindows = null)
    {
        peakWindows ??= new List<(TimeSpan, TimeSpan)>();

        var sorted = orderActivities.OrderBy(a => TimeSpan.Parse(a.Timestamp)).ToList();

        HashSet<string> activeOrders = new();
        HashSet<string> atStoreOrders = new();
        HashSet<string> inDeliveryOrders = new();
        HashSet<string> waitingOrders = new();
        decimal totalPay = 0m;

        for (int i = 0; i < sorted.Count; i++)
        {
            var activity = sorted[i];

            // Calculate pay for the interval since the previous event
            if (i > 0)
            {
                var prevTime = TimeSpan.Parse(sorted[i - 1].Timestamp);
                var currTime = TimeSpan.Parse(activity.Timestamp);
                int ongoingCount = activeOrders.Count - atStoreOrders.Count - waitingOrders.Count;

                // Split interval by peak windows and calculate pay for each sub-interval
                var subIntervals = SplitByPeakWindows(prevTime, currTime, peakWindows);
                foreach (var (subStart, subEnd, isPeak) in subIntervals)
                {
                    int minutes = (int)(subEnd - subStart).TotalMinutes;
                    decimal rate = isPeak ? basePayPerMinute * 2 : basePayPerMinute;
                    decimal intervalPay = ongoingCount * rate * minutes;
                    totalPay += intervalPay;

                    string peakLabel = isPeak ? ", PEAK" : "";
                    Console.WriteLine($"{subStart:hh\\:mm} - {subEnd:hh\\:mm}: " +
                        $"{ongoingCount} active order(s){peakLabel}, {minutes} min, pay = {intervalPay:C}");
                }
            }

            // Update state based on event type
            if (activity.EventType == "accepted")
            {
                activeOrders.Add(activity.OrderId);
            }
            else if (activity.EventType == "arrived_at_pickup")
            {
                waitingOrders.Remove(activity.OrderId);
                atStoreOrders.Add(activity.OrderId);
            }
            else if (activity.EventType == "picked_up")
            {
                bool wasAtStore = atStoreOrders.Remove(activity.OrderId);
                waitingOrders.Remove(activity.OrderId);
                inDeliveryOrders.Add(activity.OrderId);

                // Direct pickup (no arrived_at_pickup): other accepted orders become waiting
                if (!wasAtStore)
                {
                    foreach (var orderId in activeOrders)
                    {
                        if (!inDeliveryOrders.Contains(orderId) &&
                            !atStoreOrders.Contains(orderId) &&
                            !waitingOrders.Contains(orderId))
                        {
                            waitingOrders.Add(orderId);
                        }
                    }
                }
            }
            else if (activity.EventType == "fulfilled")
            {
                activeOrders.Remove(activity.OrderId);
                inDeliveryOrders.Remove(activity.OrderId);

                // If no more orders in delivery, waiting orders become active again
                if (inDeliveryOrders.Count == 0)
                    waitingOrders.Clear();
            }
        }

        return totalPay;
    }

    private List<(TimeSpan Start, TimeSpan End, bool IsPeak)> SplitByPeakWindows(
        TimeSpan start, TimeSpan end, List<(TimeSpan Start, TimeSpan End)> peakWindows)
    {
        // Collect all split points within the interval
        var splitPoints = new SortedSet<TimeSpan> { start, end };
        foreach (var (pStart, pEnd) in peakWindows)
        {
            if (pStart > start && pStart < end) splitPoints.Add(pStart);
            if (pEnd > start && pEnd < end) splitPoints.Add(pEnd);
        }

        var result = new List<(TimeSpan, TimeSpan, bool)>();
        var points = splitPoints.ToList();

        for (int i = 0; i < points.Count - 1; i++)
        {
            var subStart = points[i];
            var subEnd = points[i + 1];
            // Use midpoint to determine if sub-interval falls within a peak window [start, end)
            var mid = subStart + (subEnd - subStart) / 2;
            bool isPeak = peakWindows.Any(pw => mid >= pw.Start && mid < pw.End);
            result.Add((subStart, subEnd, isPeak));
        }

        return result;
    }
}