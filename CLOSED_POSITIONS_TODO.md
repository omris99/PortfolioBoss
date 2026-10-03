# Closed Positions — Session Implementation Plan

> **למי שקורא זאת בסשן חדש:** מדריך מימוש לפיצ'ר "עסקאות שנסגרו": רשימה של כל פוזיציה שנקנתה ונמכרה עד אפס,
> עם הרווח הממומש שלה, והזנה ידנית של עסקאות סגורות ש-PortfolioBoss לא ראה (נמכרו לפני הסנכרון הראשון).
> נבנה על התשתית של [HOLDING_DETAILS_TODO.md](HOLDING_DETAILS_TODO.md) (סשנים 0–7).
>
> סיכון לכל פריט: 🟢 נמוך | 🟡 בינוני | 🔴 גבוה. **✅ ליד מספר הפריט = בוצע.**

---

## החלטות שהתקבלו (02.10.2026, לא לפתוח מחדש)

1. **עסקה סגורה = position period שלם:** מהקנייה שפותחת פוזיציה אפסית ועד המכירה שמחזירה אותה לאפס. בקוד זה
   `PositionPeriod` — עד סשן 1 נקרא "episode", והשם הוחלף לבקשת עומרי (02.10.2026). מכירה חלקית בתוך החזקה שעדיין פתוחה
   **לא** מוצגת כעסקה סגורה (כך לא צריך לבחור שיטת עלות — ממוצע או FIFO).
2. **מעסקאות קיימות — נגזר, לא נשמר:** כמו `firstBuyDate`. כולל סבב שנסגר בתוך החזקה שעדיין פתוחה (מכרתי הכול ב-2025, קניתי שוב ב-2026).
3. **הזנה ידנית — טבלה נפרדת `manual_closed_position`**, שורה אחת = סבב שלם (קנייה אחת, מכירה אחת). לא נוגעים בטבלת
   `holding` ובסנכרון: הכלל "רק הסנכרון יוצר החזקות" (CLAUDE.md) נשאר כמו שהוא. השם "position" ולא "trade" — בקוד `trade` היא קנייה *או* מכירה אחת.
4. **רווח ממומש = תמורת המכירות − עלות הקניות − עמלות.** מחיר חסר באחת העסקאות של הסבב ← הרווח `null` (מוצג "—"), לא ניחוש.
   עמלה שלא הוזנה נחשבת 0.
5. **עמלה:** עמודה אופציונלית ב-`trade`, ובטבלה הידנית סכום אחד לסבב כולו.
6. **UI:** אזור "Closed positions" מתחת לטבלת ה-Positions. המתג "Show closed" נשאר — שם משלימים עסקאות של החזקה סגורה.
7. **לא משתמשים ב-`realizedPnl` של IB:** הוא לא מבחין בין סבבים, ואין אותו לעסקאות ידניות.

## עקרונות קבועים

- כל עקרונות HOLDING_DETAILS_TODO.md בתוקף: read-only מול IB, `GET /api/portfolio` רק מתווסף, JSON בלבד בכתיבה, להסביר לפני עריכה.
- **החישוב כתוב פעם אחת:** `domain.ClosedPosition` מחשב ממוצעים, רווח ואחוז — גם לסבב שנגזר מעסקאות וגם לשורה ידנית.
- Git / changelog / "עדכונים של סוף סשן" — רק ביוזמת המשתמש.

---

## סשן 1 — רשימת העסקאות הסגורות מהעסקאות הקיימות (בלי שינוי סכמה)

> **המטרה:** כל position period שנסגר מופיע ב-API וב-UI עם הרווח שלו. עובד על הנתונים שכבר ב-DB.
>
> **סטטוס (02.10.2026):** 1.1–1.5 נבנו על branch `maven-spring-boot`, עדיין לא committed. `mvn -q test` ירוק — 88 בדיקות (15 חדשות);
> `npm run build` ירוק; רינדור בצד השרת של הטבלה והסכומים עם נתונים לדוגמה. ⬜ בדיקה בדפדפן — בידי עומרי. תוספות קטנות שלא היו בתוכנית:
> `HoldingEntity.symbol()` / `currency()` (accessors), `TradeFact.amount()`, ו-`HoldingPeriod` עבר מ-`PositionsTable` לקובץ משלו כי שתי הטבלאות משתמשות בו.
>
> **תיקון אחרי בדיקת הדפדפן (03.10.2026):** סבב שנמכר בו יותר ממה שנקנה (15 נקנו, 25 נמכרו) הראה רווח מנופח (+1,400 במקום +200) ומחיר
> מכירה ממוצע שלא היה באף מכירה. עכשיו `ClosedPosition` שומר גם `soldQuantity`: Avg sell מחושב לפי הכמות שנמכרה, הרווח והאחוז `null`,
> ו-`ClosedPositionResponse.warning` (בסוף) מחזיר הודעה שמוצגת בשורה כתומה גלויה מתחת לשורה ("Sold 25 shares but bought 15…"). בכותרת:
> "(n without prices)" ו-"(n to check)" בנפרד.

### 1.1 🟡 `domain`: `ClosedPosition` וחלוקה ל-position periods ב-`HoldingHistory`

- `TradeFact` מקבל `price` (יכול להיות `null`).
- `record ClosedPosition(openDate, closeDate, quantity, buyCost, sellProceeds)` — הסכומים הגולמיים; ממנו נגזרים
  `holdingDays()`, `averageBuyPrice()`, `averageSellPrice()`, `realizedPnl()`, `realizedPnlPercent()`.
  `buyCost` / `sellProceeds` הם `null` אם לאחת מהעסקאות בצד הזה אין מחיר.
- `HoldingHistory.of` מחלק את העסקאות ל-position periods (record פרטי `PositionPeriod`: העסקאות שלו ודגל "נסגר"), ומהם גוזר:
  `firstBuyDate` / `lastSellDate` מה-period האחרון (כמו קודם), ו-`closedPositions` מכל period שנסגר.
- **⚠️ שינוי התנהגות קטן ומכוון:** מכירה שמורידה את הכמות *מתחת* לאפס (מכירת יתר — טעות הזנה) סוגרת את ה-period, כמו שהתוכנית המקורית
  (3.1) הגדירה. עד היום הכמות נשארה שלילית והקנייה הבאה לא פתחה period חדש. `netQuantity` לא משתנה, ולכן `QUANTITY_MISMATCH` ממשיך להצביע על הטעות.

### 1.2 🟢 `HoldingEntity.tradeHistory()`

`HoldingHistory` של השורה, מחושב מהעסקאות שלה — כך ש-`HoldingResponse` והעסקאות הסגורות לא משכפלים את ההמרה
`TradeEntity → TradeFact → HoldingHistory` (באותה רוח של `toIbHolding()`).

### 1.3 🟢 API: `ClosedPositionResponse` ו-`PortfolioResponse.closedPositions`

- `ClosedPositionResponse(holdingId, symbol, currency, sector, openDate, closeDate, holdingDays, quantity, averageBuyPrice,
  averageSellPrice, realizedPnl, realizedPnlPercent)`.
- `closedPositions` נוסף **בסוף** `PortfolioResponse` (ברמה העליונה — בסשן 2 יצטרפו שורות ידניות שלא שייכות לאף החזקה).

### 1.4 🟢 בדיקות

`ClosedPositionTest` (החישובים), `HoldingHistoryTest` (חלוקה לסבבים), `PortfolioReadServiceTest` (מה-DB ועד ה-response),
`PortfolioControllerTest` (שמות השדות ב-JSON).

### 1.5 🟡 UI: אזור "Closed positions"

- `types/portfolio.ts`: `ClosedPosition` ו-`closedPositions`.
- `components/ClosedPositionsTable.tsx`: ממוין לפי תאריך מכירה, החדש קודם (סדר קבוע בגרסה הראשונה). עמודות: Symbol, Sector, Bought,
  Sold, Held, Qty, Avg buy, Avg sell, Realized P&L, %.
- ב-`App`: אזור חדש מתחת ל-Positions, עם מספר העסקאות וסך הרווח הממומש — לכל מטבע בנפרד.

**בדיקת אימות סשן 1:** `mvn -q test` ו-`npm run build` ירוקים; בדפדפן — החזקה עם קנייה ומכירה מלאה מופיעה ברשימה עם הרווח הנכון.

---

## סשן 2 — backend: עמלות ועסקאות סגורות ידניות

### 2.1 🟡 מיגרציה `V2__closed_positions.sql`

```sql
ALTER TABLE trade ADD COLUMN commission NUMERIC(20,6) CHECK (commission >= 0);

CREATE TABLE manual_closed_position (
    id          BIGSERIAL     PRIMARY KEY,
    symbol      VARCHAR(32)   NOT NULL,
    currency    VARCHAR(8)    NOT NULL,
    sector      VARCHAR(60),
    quantity    NUMERIC(20,6) NOT NULL CHECK (quantity > 0),
    buy_date    DATE          NOT NULL,
    buy_price   NUMERIC(20,6) NOT NULL CHECK (buy_price >= 0),
    sell_date   DATE          NOT NULL,
    sell_price  NUMERIC(20,6) NOT NULL CHECK (sell_price >= 0),
    commission  NUMERIC(20,6) CHECK (commission >= 0),
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (sell_date >= buy_date)
);
```

### 2.2 🟡 עמלה על עסקה

`TradeEntity`, `TradeRequest`, `TradeResponse` (תוספת בסוף), `TradeFact`; `ClosedPosition` מקבל `commissions` ומפחית אותן מהרווח.

### 2.3 🟡 `ManualClosedPositionEntity` + repository + כתיבה

- `POST /api/manual-closed-positions` (201), `PUT /api/manual-closed-positions/{id}` (200), `DELETE /api/manual-closed-positions/{id}` (204) —
  JSON בלבד, `@Valid`, אותן מגבלות כמו `TradeRequest`, ו"תאריך מכירה לא לפני תאריך קנייה".
- `ClosedPositionResponse` מקבל `source` (`TRADES` / `MANUAL`) ואת ה-id של השורה הידנית; `PortfolioReadService` מוסיף את השורות הידניות לרשימה.

### 2.4 🟢 בדיקות

Controller (קודי סטטוס, ולידציה, JSON בלבד) ו-Service (מה נשמר), באותה חלוקה כמו `HoldingWriteControllerTest` / `HoldingWriteServiceTest`.

---

## סשן 3 — UI: עמלה בטופס העסקה וטופס עסקה סגורה ידנית

- שדה "Commission" אופציונלי ב-`TradeForm`, ועמודה ב-`TradesPanel`.
- כפתור "+ Add closed position" באזור העסקאות הסגורות, טופס הוספה/עריכה, ומחיקה. שורה ידנית מסומנת "manual".
- שורה שנגזרת מעסקאות לא נערכת שם — מתקנים את העסקאות של ההחזקה (tooltip אומר את זה).

---

## מחוץ לתחום (כרגע)

- זיהוי כפילות בין שורה ידנית לסבב שנגזר מעסקאות של אותה מניה.
- מיון לפי עמודה בטבלת העסקאות הסגורות.
- רווח ממומש של מכירות חלקיות, FIFO / tax lots.
