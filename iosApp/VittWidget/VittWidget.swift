import WidgetKit
import SwiftUI

/// What the app last published, read out of the shared App Group.
///
/// The widget never opens the database. It runs in its own process with its own
/// container, so the app's storage is invisible to it; the app writes four
/// fields into the group and the widget renders those. Parsed defensively,
/// because after an app update the widget may still be the older build, and a
/// crash here lands on somebody's home screen.
struct Snapshot {
    let currencyCode: String
    let amount: String
    let note: String
    /// Minor units left to spend, and the last day of the month as an epoch day.
    /// Kept raw so the per-day figure can be recomputed at each midnight
    /// without the app running.
    let leftMinor: Int64
    let monthEndDay: Int
    let exponent: Int
    let symbol: String

    /// What is left per day for the rest of the month, for a given day.
    ///
    /// The figure the widget exists for. It changes at every midnight whether
    /// or not anybody opened the app, and it goes *up* on a day nothing was
    /// spent, so restraint is rewarded by arithmetic rather than by a badge.
    /// Framed as permission, never as a verdict: PLAN.md §0 records the ostrich
    /// effect as the reason people stop looking at money apps at all.
    func roomPerDay(on day: Int) -> String? {
        guard leftMinor > 0 else { return nil }
        let daysLeft = max(1, monthEndDay - day + 1)
        let perDay = leftMinor / Int64(daysLeft)
        let major = exponent == 0
            ? String(perDay)
            : String(format: "%.2f", Double(perDay) / 100.0)
        return "\(symbol)\(major)"
    }

    func daysLeft(on day: Int) -> Int { max(1, monthEndDay - day + 1) }

    static func load() -> Snapshot? {
        guard
            let defaults = UserDefaults(suiteName: "group.ie.shoonya.vitt"),
            let raw = defaults.string(forKey: "snapshot")
        else { return nil }

        let parts = raw.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 5, !parts[0].isEmpty,
              let spent = Int64(parts[1]),
              let budget = Int64(parts[2]),
              let days = Int(parts[3]),
              let monthEnd = Int(parts[4])
        else { return nil }

        // Two decimals for everything the app carries except yen, which has
        // none. Kept here rather than passed across, because the payload is
        // deliberately four plain fields.
        let code = parts[0]
        let exponent = code == "JPY" ? 0 : 2
        let major = exponent == 0
            ? String(spent)
            : String(format: "%.2f", Double(spent) / 100.0)
        let symbol = ["EUR": "€", "INR": "₹", "GBP": "£", "USD": "$", "JPY": "¥"][code] ?? ""

        // Never a scolding. The Gentle tier in PLAN.md §5 rules out making
        // someone feel worse for having spent, so over budget is a fact.
        let note: String
        if budget < 0 {
            note = days > 0 ? "\(days) days recorded" : ""
        } else if budget == 0 {
            note = ""
        } else if spent > budget {
            note = "Over the budget"
        } else {
            let left = Int((1.0 - Double(spent) / Double(budget)) * 100)
            note = "\(left)% of the budget left"
        }

        return Snapshot(
            currencyCode: code,
            amount: "\(symbol)\(major)",
            note: note,
            leftMinor: budget < 0 ? 0 : budget - spent,
            monthEndDay: monthEnd,
            exponent: exponent,
            symbol: symbol
        )
    }
}

struct Entry: TimelineEntry {
    let date: Date
    let snapshot: Snapshot?
    /// The epoch day this entry renders. One per midnight, so the per-day
    /// figure recalculates itself with no app launch and no refresh budget.
    let day: Int
}

/// Days since the Unix epoch, in the user's own calendar.
///
/// UTC would tip over at the wrong moment for anyone east or west of it, and
/// the figure is about "today" as the person experiences it.
private func epochDay(_ date: Date) -> Int {
    Int(Calendar.current.startOfDay(for: date).timeIntervalSince1970 / 86_400)
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> Entry {
        Entry(date: Date(), snapshot: nil, day: epochDay(Date()))
    }

    func getSnapshot(in context: Context, completion: @escaping (Entry) -> Void) {
        completion(Entry(date: Date(), snapshot: Snapshot.load(), day: epochDay(Date())))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) {
        let snapshot = Snapshot.load()
        let now = Date()
        let calendar = Calendar.current
        let startOfToday = calendar.startOfDay(for: now)

        // One entry per midnight to the end of the month, capped.
        //
        // This is what makes the figure worth glancing at: it rises on a day
        // nothing was spent, and it does so on its own. WidgetKit renders a
        // future entry without waking the app, so none of this costs a refresh
        // budget. Spending inside the app republishes and reloads anyway.
        var entries: [Entry] = []
        let horizon = min(14, max(1, (snapshot?.monthEndDay ?? 0) - epochDay(now) + 1))
        for offset in 0..<horizon {
            guard let date = calendar.date(byAdding: .day, value: offset, to: startOfToday) else { break }
            entries.append(Entry(date: offset == 0 ? now : date, snapshot: snapshot, day: epochDay(date)))
        }
        if entries.isEmpty {
            entries = [Entry(date: now, snapshot: snapshot, day: epochDay(now))]
        }

        // Ask again at the end of the run rather than never: the month rolls
        // over and the entries would otherwise describe a month that has ended.
        completion(Timeline(entries: entries, policy: .atEnd))
    }
}

struct VittWidgetView: View {
    @Environment(\.widgetFamily) private var family
    var entry: Entry
    /// False for the widget that leads with the running total instead.
    var showRoom: Bool = true

    var body: some View {
        switch family {
        case .accessoryRectangular, .accessoryInline:
            lockScreenRectangular
        case .accessoryCircular:
            lockScreenCircular
        default:
            homeScreen
        }
    }

    /// The per-day figure leads, because it is the only number here anyone can
    /// act on. "Spent so far" is a fact about the past that changes only when
    /// the user does something, which is no reason to look twice.
    private var lead: (String, String)? {
        guard let snapshot = entry.snapshot else { return nil }
        if showRoom, let perDay = snapshot.roomPerDay(on: entry.day) {
            let days = snapshot.daysLeft(on: entry.day)
            return (perDay, days == 1 ? "for the last day" : "a day for \(days) days")
        }
        // No budget, or none of it left. Falls back to the plain total rather
        // than inventing an allowance that is not there.
        return (snapshot.amount, snapshot.note.isEmpty ? "\(snapshot.currencyCode) this month" : snapshot.note)
    }

    private var homeScreen: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let (figure, caption) = lead {
                Text(figure)
                    .font(.system(size: 28, weight: .bold))
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                Text(caption)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            } else {
                // Says so plainly rather than showing a confident zero, which
                // would read as a real figure.
                Text("—").font(.system(size: 28, weight: .bold))
                Text("Nothing recorded yet").font(.caption).foregroundStyle(.secondary)
            }

            Spacer(minLength: 6)

            Link(destination: URL(string: "vitt://add")!) {
                Text("+  Log a spend")
                    .font(.system(size: 14, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(Color(red: 0.42, green: 0.30, blue: 0.95))
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .containerBackground(for: .widget) { Color(red: 0.98, green: 0.97, blue: 0.945) }
    }

    /// The Lock Screen is where glances actually happen: seen dozens of times a
    /// day without unlocking, which is exactly when the figure is useful — just
    /// before deciding to spend, rather than after.
    private var lockScreenRectangular: some View {
        VStack(alignment: .leading, spacing: 1) {
            if let (figure, caption) = lead {
                Text(figure).font(.headline)
                Text(caption).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            } else {
                Text("VITT").font(.headline)
                Text("Nothing recorded yet").font(.caption2).foregroundStyle(.secondary)
            }
        }
        .containerBackground(for: .widget) { Color.clear }
    }

    private var lockScreenCircular: some View {
        VStack(spacing: 0) {
            Text(lead?.0 ?? "—")
                .font(.system(size: 14, weight: .semibold))
                .minimumScaleFactor(0.4)
                .lineLimit(1)
            Text("a day").font(.system(size: 9)).foregroundStyle(.secondary)
        }
        .containerBackground(for: .widget) { Color.clear }
    }
}

/// What is left to spend per day. The figure that changes on its own.
struct RoomWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ie.shoonya.vitt.widget.room", provider: Provider()) { entry in
            VittWidgetView(entry: entry, showRoom: true)
        }
        .configurationDisplayName("Room left")
        .description("What is left to spend per day, and one tap to log.")
        .supportedFamilies([
            .systemSmall,
            .systemMedium,
            // The Lock Screen, where a glance costs nothing.
            .accessoryRectangular,
            .accessoryCircular,
        ])
    }
}

/// The running total, for anyone who wants the plain fact instead.
struct SpentWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ie.shoonya.vitt.widget.spent", provider: Provider()) { entry in
            VittWidgetView(entry: entry, showRoom: false)
        }
        .configurationDisplayName("Spent this month")
        .description("What you have spent so far, and one tap to log.")
        .supportedFamilies([
            .systemSmall,
            .systemMedium,
            .accessoryRectangular,
            .accessoryCircular,
        ])
    }
}

/// Two widgets rather than one with a setting.
///
/// A widget's whole job is to be glanceable, and someone who wants the running
/// total and someone who wants the daily allowance are asking different
/// questions. Offering both, placeable together, is cheaper than a
/// configuration screen and better than guessing.
@main
struct VittWidgets: WidgetBundle {
    var body: some Widget {
        RoomWidget()
        SpentWidget()
    }
}
