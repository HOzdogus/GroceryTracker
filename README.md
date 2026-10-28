# Grocery Tracker

Android app that tracks Asda and Sainsbury's prices and notifies you when they drop.

- **Add items** by sharing a product page from Chrome or the shop apps ("Track price"), or tap **Add** and paste a link.
- **Daily check** around 8am (needs a network connection). Items that couldn't be checked are retried 2 and 6 hours later. A failed check never counts as a price change.
- **Notifications** for drops and target prices. Several at once are combined into one summary.
- **Offer prices** (best effort): Asda Rollback "was" prices and Sainsbury's Nectar prices. The detail screen's menu has **Page diagnostics**, which shows what the detection saw.

## Building

Every push builds a debug APK in GitHub Actions (**Build APK** workflow). Download it from the run's artifacts.
The debug keystore is committed (`app/debug.keystore`), so each build installs over the last.

Prices are read in an off-screen WebView from the page's JSON-LD product data. Server-side requests don't work: Asda blocks them.
