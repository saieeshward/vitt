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

    static func load() -> Snapshot? {
        guard
            let defaults = UserDefaults(suiteName: "group.ie.shoonya.vitt"),
            let raw = defaults.string(forKey: "snapshot")
        else { return nil }

        let parts = raw.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 4, !parts[0].isEmpty,
              let spent = Int64(parts[1]),
              let budget = Int64(parts[2]),
              let days = Int(parts[3])
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

        return Snapshot(currencyCode: code, amount: "\(symbol)\(major)", note: note)
    }
}

struct Entry: TimelineEntry {
    let date: Date
    let snapshot: Snapshot?
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> Entry {
        Entry(date: Date(), snapshot: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (Entry) -> Void) {
        completion(Entry(date: Date(), snapshot: Snapshot.load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) {
        // One entry, never refreshed on a schedule. The figures only change
        // when the app writes, and the app calls reloadAllTimelines when it
        // does, so a polling budget spent here would buy nothing.
        completion(Timeline(entries: [Entry(date: Date(), snapshot: Snapshot.load())], policy: .never))
    }
}

struct VittWidgetView: View {
    var entry: Entry

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let snapshot = entry.snapshot {
                Text("\(snapshot.currencyCode) this month")
                    .font(.caption).foregroundStyle(.secondary)
                Text(snapshot.amount)
                    .font(.system(size: 26, weight: .bold))
                    .minimumScaleFactor(0.6)
                    .lineLimit(1)
                if !snapshot.note.isEmpty {
                    Text(snapshot.note).font(.caption).foregroundStyle(.secondary)
                }
            } else {
                // Says so plainly rather than showing a confident zero, which
                // would read as a real figure.
                Text("VITT").font(.caption).foregroundStyle(.secondary)
                Text("—").font(.system(size: 26, weight: .bold))
                Text("Nothing recorded yet").font(.caption).foregroundStyle(.secondary)
            }

            Spacer(minLength: 6)

            // The reason the widget exists. Logging has to be reachable without
            // opening the app: a manual tracker fails when capture costs more
            // than the moment is worth.
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
}

@main
struct VittWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ie.shoonya.vitt.widget", provider: Provider()) { entry in
            VittWidgetView(entry: entry)
        }
        .configurationDisplayName("Spending")
        .description("This month's spending, and one tap to log.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
