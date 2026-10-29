# Grocery Tracker

Android app that tracks grocery prices on any shop's website and notifies you when they drop. It was built for Asda and Sainsbury's.

- **Add items** by sharing a product page from Chrome or a shop's app ("Track price"), or tap **Add** and paste a link.
- **Any website** that publishes standard product data (schema.org JSON-LD, or `product:price` / `itemprop="price"` tags) works. Well-known UK shops get their own name and badge colour; other sites are named by their domain. Pages with no structured price are refused rather than guessed. Some sites may block the hidden browser or need a store chosen first.
- **Groups** put the same product from different shops into one row showing the cheapest shop. Tap a group to see every shop's price and their history together. After adding an item you're asked whether to group it. You can also change it from the item screen, or use **Add another shop** on a group.
- **Daily check** around 8am (needs a network connection). Items that couldn't be checked are retried 2 and 6 hours later. A failed check never counts as a price change.
- **Notifications** for drops and target prices. Several at once are combined into one summary.
- **Offer prices** (best effort): Asda Rollback "was" prices and Sainsbury's Nectar prices. The detail screen's menu has **Page diagnostics**, which shows what the detection saw.

## Building

Every push builds a debug APK in GitHub Actions (**Build APK** workflow). Download it from the run's artifacts.
The debug keystore is committed (`app/debug.keystore`), so each build installs over the last.

Prices are read in an off-screen WebView from the page's structured product data. Server-side requests don't work: Asda blocks them.
