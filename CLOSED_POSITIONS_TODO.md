# Closed Positions — Session Implementation Plan

> **למי שקורא זאת בסשן חדש:** מדריך מימוש לפיצ'ר "עסקאות שנסגרו": רשימה של כל פוזיציה שנקנתה ונמכרה עד אפס,
> עם הרווח הממומש שלה, והזנה ידנית של עסקאות סגורות ש-PortfolioBoss לא ראה (נמכרו לפני הסנכרון הראשון).
> נבנה על התשתית של [HOLDING_DETAILS_TODO.md](HOLDING_DETAILS_TODO.md) (סשנים 0–7).
>
> סיכון לכל פריט: 🟢 נמוך | 🟡 בינוני | 🔴 גבוה. **✅ ליד מספר הפריט = בוצע.**

---

## החלטות שהתקבלו (02.10.2026, לא לפתוח מחדש)

1. **עסקה סגורה = position period שלם:** מהקנייה שפותחת פוזיציה אפסית ועד המכירה שמחזירה אותה לאפס. בקוד זה
   `PositionPeriod` — עד סשן 1 נקרא "episode", והשם הוחלף לבקשת עומרי (02.10.2026). ~~מכירה חלקית בתוך החזקה שעדיין פתוחה
   **לא** מוצגת כעסקה סגורה~~ — הוחלף ב-03.10.2026 בהחלטות 9–10: גם מכירה חלקית נספרת, לפי מחיר ממוצע.
2. **מעסקאות קיימות — נגזר, לא נשמר:** כמו `firstBuyDate`. כולל סבב שנסגר בתוך החזקה שעדיין פתוחה (מכרתי הכול ב-2025, קניתי שוב ב-2026).
3. **הזנה ידנית — טבלה נפרדת, לא `holding`:** לא נוגעים בטבלת `holding` ובסנכרון: הכלל "רק הסנכרון יוצר החזקות" (CLAUDE.md) נשאר
   כמו שהוא. ~~שורה אחת = סבב שלם (קנייה אחת, מכירה אחת), בטבלה `manual_closed_position`~~ — הוחלף ב-03.10.2026 בהחלטה 11: פוזיציה
   ידנית עם קניות ומכירות משלה.
4. **רווח ממומש = תמורת המכירות − עלות הקניות − עמלות.** מחיר חסר באחת העסקאות של הסבב ← הרווח `null` (מוצג "—"), לא ניחוש.
   ~~עמלה שלא הוזנה נחשבת 0~~ — הוחלף ב-03.10.2026 בעמלת ברירת מחדל (החלטה 8).
5. **עמלה:** עמודה ב-`trade` (אופציונלית בבקשה), ובטבלה הידנית סכום אחד לסבב כולו.
8. **עמלת ברירת מחדל (03.10.2026, עומרי):** סנט למניה עם מינימום 5$ לפקודה — אותו כלל כמו `calculateOrderCommission` ב-IBBot
   (100 מניות → 5$, 600 מניות → 6$). **נשמרת ב-DB בזמן הכתיבה**, לא מחושבת בכל קריאה: עסקה בלי עמלה נשמרת עם ברירת המחדל, ואפשר
   לתקן אותה (גם ל-0). עריכה בלי עמלה מחשבת אותה מחדש לפי הכמות. שורה ידנית = שתי פקודות (קנייה + מכירה). העסקאות שהיו ב-DB לפני
   כן קיבלו את ברירת המחדל במיגרציה, ולכן העמודה `NOT NULL`.

### החלטות 03.10.2026, אחרי בדיקת הדפדפן של סשן 3 (עומרי, לא לפתוח מחדש)

9. **גם מכירה חלקית היא רווח ממומש, לפי מחיר ממוצע** (לא FIFO — הוצגו שתיהן עם דוגמה, ועומרי בחר ממוצע). כל מניה שנמכרת "עולה" את
   העלות הממוצעת של המניות שהוחזקו ברגע המכירה. דוגמה: קנייה 10 ב-100, קנייה 10 ב-200, מכירה 10 ב-180 ← ממוצע 150, רווח +300.
   בסבב שנסגר עד הסוף התוצאה זהה לחישוב של היום (תמורה − עלות − עמלות).
10. **שורה = סבב** (position period) שיש בו לפחות מכירה אחת, עם מחיר קנייה ממוצע ומחיר מכירה ממוצע. סבב שעוד לא נסגר מסומן
    "partial · still holding N", והשורה שלו "גדלה" בכל מכירה עד שהוא נסגר. **לחיצה על שורה פותחת את העסקאות של הסבב.** הדוגמה של עומרי:
    שלוש קניות של 9988.HK (221, 162.10, 140.40) ומכירה אחת של 300 ב-177.70 ← שורה אחת: ממוצע 174.50, רווח +930 HKD (אחרי 30 עמלות).
11. **עסקה ידנית = פוזיציה ידנית** (`manual_position`): Symbol, Currency, Sector, Note, **וקניות ומכירות משלה** — באותה טבלת `trade`,
    כשכל עסקה שייכת להחזקה *או* לפוזיציה ידנית. החישוב, טופס העסקה ו-PUT/DELETE של עסקה משותפים לשתיהן. 5 השורות שכבר הוזנו מומרות
    אוטומטית (קנייה אחת + מכירה אחת, העמלה חצי-חצי); את שלוש השורות של 9988.HK עומרי מאחד אחר כך ביד (או דרך ה-API, באישורו).
12. **איפה עורכים:** עסקאות של פוזיציה ידנית — בשורה הפתוחה בטבלת העסקאות הסגורות. עסקאות של החזקה — רק ב-Positions, כמו היום (בשורה
    הפתוחה הן לקריאה בלבד).
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

> **סטטוס (03.10.2026):** 2.1–2.4 נבנו על branch `maven-spring-boot`, עדיין לא committed. `mvn -q test` ירוק — 118 בדיקות (29 חדשות).
> V2 רצה על `portfolioboss_test`; על ה-DB האמיתי היא תרוץ ב-`./run.sh` הבא (7 העסקאות שם יקבלו 5$ כל אחת). ה-UI לא נגע (סשן 3).
> שינויים מהתוכנית, כולם אושרו לפני הבנייה: עמלת ברירת מחדל (החלטה 8) במקום 0; `ClosedPositionResponse` קיבל גם `commissions` ו-`note`
> (טופס העריכה של סשן 3 צריך אותם — PUT מחליף הכול) ו-`holdingId` נעשה `Long` (`null` בשורה ידנית); ה-endpoints ב-
> `ClosedPositionWriteController` / `ClosedPositionWriteService` חדשים ולא ב-`HoldingWrite*`; "מכירה לא לפני קנייה" היא `@AssertTrue`
> ב-request (ההודעה: `sellDateOnOrAfterBuyDate: the sell date is before the buy date`); symbol ו-currency נשמרים באותיות גדולות בלי
> רווחים; `trimmedOrNull` עבר ל-`Utils`, ליד `calculateOrderCommission`.

### 2.1 🟡✅ מיגרציה `V2__closed_positions.sql`

```sql
ALTER TABLE trade ADD COLUMN commission NUMERIC(20,6) CHECK (commission >= 0);
UPDATE trade SET commission = ROUND(GREATEST(5, quantity * 0.01), 2);   -- ברירת המחדל לעסקאות שכבר קיימות
ALTER TABLE trade ALTER COLUMN commission SET NOT NULL;

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
    commission  NUMERIC(20,6) NOT NULL CHECK (commission >= 0),
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (sell_date >= buy_date)
);
```

### 2.2 🟡✅ עמלה על עסקה

`TradeEntity`, `TradeRequest`, `TradeResponse` (תוספת בסוף), `TradeFact`; `ClosedPosition` מקבל `commissions` ומפחית אותן מהרווח.
ברירת המחדל: `Utils.calculateOrderCommission(quantity)`, ש-`HoldingWriteService` שם כשלא נשלחה עמלה.

### 2.3 🟡✅ `ManualClosedPositionEntity` + repository + כתיבה

- `POST /api/manual-closed-positions` (201), `PUT /api/manual-closed-positions/{id}` (200), `DELETE /api/manual-closed-positions/{id}` (204) —
  JSON בלבד, `@Valid`, אותן מגבלות כמו `TradeRequest`, ו"תאריך מכירה לא לפני תאריך קנייה". POST ו-PUT מחזירים את השורה כ-`ClosedPositionResponse`.
- `ClosedPositionResponse` מקבל (בסוף) `commissions`, `source` (`TRADES` / `MANUAL`, ה-enum `ClosedPositionSource`), `manualClosedPositionId`
  ו-`note`; `PortfolioReadService` מוסיף את השורות הידניות לרשימה, אחרי אלה שנגזרו מעסקאות.
- `ManualClosedPositionEntity.toClosedPosition()` — החישוב עובר דרך `ClosedPosition`, כמו בסבב שנגזר מעסקאות.

### 2.4 🟢✅ בדיקות

Controller (קודי סטטוס, ולידציה, JSON בלבד) ו-Service (מה נשמר), באותה חלוקה כמו `HoldingWriteControllerTest` / `HoldingWriteServiceTest`:
`ClosedPositionWriteControllerTest`, `ClosedPositionWriteServiceTest`; וגם `UtilsTest` (הנוסחה), עמלות ב-`ClosedPositionTest` / `HoldingHistoryTest` /
`HoldingWrite*Test`, והמפתחות החדשים ב-`PortfolioControllerTest`.

---

## סשן 3 — UI: עמלה בטופס העסקה וטופס עסקה סגורה ידנית

> **סטטוס (03.10.2026):** נבנה על branch `maven-spring-boot`, עדיין לא committed (יחד עם סשן 2). `npm run build` ירוק; רינדור בצד השרת
> של האזור, טופס העריכה ופאנל העסקאות עם נתונים לדוגמה. ⬜ בדיקה בדפדפן — בידי עומרי (`./run.sh` הבא מריץ גם את V2 על ה-DB האמיתי).
> קבצים: `ManualClosedPositionForm.tsx` חדש; `ClosedPositionsSection` עבר מ-`App.tsx` לקובץ משלו (עם ה-state של הטופס והמחיקה), ו-`EmptyBox`
> לקובץ משלו. תוספת שלא הייתה בתוכנית: `lib/formInput.ts` — `localTodayIsoDate`, `numberOrNull`, `trimmedOrNull`, `INPUT_CLASS`, שעברו
> מ-`TradeForm` כי שני הטפסים צריכים אותם. ב-`ClosedPositionsTable` עמודת Commission, תגית "manual", ועמודת פעולות (✏️/🗑 בשורה ידנית,
> עיפרון מחוק עם tooltip בשורה נגזרת).
>
> **אחרי בדיקת הדפדפן (03.10.2026):** עמודת Note בסוף הטבלה (נחתכת, המלאה בריחוף; "—" בשורה נגזרת); המטבע ליד ה-Realized P&L של כל
> שורה ("+40.00 USD"), כמו בסיכום; אחוזים בשתי ספרות אחרי הנקודה — `formatSignedPercent`, ולכן גם ה-% ב-Positions.

- שדה "Commission" אופציונלי ב-`TradeForm`, ועמודה ב-`TradesPanel`. שדה ריק = ברירת המחדל (השרת מחשב); בעריכה הוא מגיע מלא בעמלה השמורה.
- כפתור "+ Add closed position" באזור העסקאות הסגורות, טופס הוספה/עריכה, ומחיקה. שורה ידנית מסומנת "manual".
- שורה שנגזרת מעסקאות לא נערכת שם — מתקנים את העסקאות של ההחזקה (tooltip אומר את זה).
- מסשן 2: `types/portfolio.ts` — `Trade.commission`, ו-`ClosedPosition` מקבל `commissions`, `source`, `manualClosedPositionId`, `note`, ו-`holdingId`
  נעשה `number | null`. ה-key של שורה ב-`ClosedPositionsTable` (היום `holdingId-openDate`) צריך את `manualClosedPositionId` לשורה ידנית.

---

## סשן 4 — backend: מחיר ממוצע, מכירות חלקיות, פוזיציה ידנית עם עסקאות (החלטות 9–12)

> **המטרה:** כל סבב שיש בו מכירה הוא שורה — גם אם עוד מחזיקים חלק ממנו — עם עלות ממוצעת; ועסקה ידנית הופכת לפוזיציה ידנית עם קניות
> ומכירות משלה. **לפני שמתחילים (הצעה, ההחלטה של עומרי):** commit לסשנים 2–3 כפי שהם, כדי שהשינוי הזה יהיה שלב נפרד.
>
> **סטטוס (03.10.2026):** 4.1–4.5 נבנו על `maven-spring-boot` (אחרי b4daeab + 634f1b8), עדיין לא committed. `mvn -q test` ירוק — 131 בדיקות.
> V3 נבדקה על DB זמני עם עותק של הנתונים האמיתיים (5 הפוזיציות עם אותם ids, 10 עסקאות ידניות עם 5 + 5 עמלה, עסקאות ההחזקות לא נגעו,
> ה-id הבא 7), ורצה על `portfolioboss_test`. **על `portfolioboss` היא תרוץ ב-`./run.sh` הבא — לגבות קודם.** ⚠️ עד סשן 5 ה-UI עדיין
> קורא ל-`/api/manual-closed-positions` ול-`manualClosedPositionId`: הטבלה מוצגת, אבל הטופס והכפתורים של שורה ידנית לא עובדים.
> שמות שלא היו בתוכנית: `NewManualPositionRequest` (POST — פרטים, וקנייה ומכירה ראשונות), `ManualPositionRequest` (PUT), `ManualPositionResponse`
> (התשובה ל-POST, עם ה-id), `Utils.commissionOrDefault` (עבר מ-`HoldingWriteService`, שני ה-services צריכים אותו), `AverageCostCalculator`
> (מחלקה פנימית ב-`HoldingHistory`; השם "ledger" נדחה). הודעת מכירת היתר אומרת עכשיו "check its trades" — נכון גם לפוזיציה ידנית.
> מחיקת פוזיציה ידנית מוחקת את העסקאות שלה בקוד, לא רק דרך `ON DELETE CASCADE`.

### 4.1 🔴 מיגרציה `V3__manual_positions.sql`

```sql
CREATE TABLE manual_position (
    id          BIGSERIAL     PRIMARY KEY,
    symbol      VARCHAR(32)   NOT NULL,
    currency    VARCHAR(8)    NOT NULL,
    sector      VARCHAR(60),
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- עסקה שייכת להחזקה או לפוזיציה ידנית — לעולם לא לשתיהן. מחיקת פוזיציה ידנית מוחקת את העסקאות שלה.
ALTER TABLE trade ALTER COLUMN holding_id DROP NOT NULL;
ALTER TABLE trade ADD COLUMN manual_position_id BIGINT REFERENCES manual_position (id) ON DELETE CASCADE;
ALTER TABLE trade ADD CHECK (num_nonnulls(holding_id, manual_position_id) = 1);
CREATE INDEX trade_manual_position_date ON trade (manual_position_id, trade_date);

-- כל שורה של V2 הופכת לפוזיציה ידנית (אותו id) עם קנייה אחת ומכירה אחת; העמלה מתחלקת ביניהן בלי לאבד סנט.
INSERT INTO manual_position (id, symbol, currency, sector, note, created_at)
    SELECT id, symbol, currency, sector, note, created_at FROM manual_closed_position;
SELECT setval(pg_get_serial_sequence('manual_position', 'id'), (SELECT coalesce(max(id), 0) + 1 FROM manual_position), false);
INSERT INTO trade (manual_position_id, trade_date, side, quantity, price, commission)
    SELECT id, buy_date, 'BUY', quantity, buy_price, ROUND(commission / 2, 2) FROM manual_closed_position;
INSERT INTO trade (manual_position_id, trade_date, side, quantity, price, commission)
    SELECT id, sell_date, 'SELL', quantity, sell_price, commission - ROUND(commission / 2, 2) FROM manual_closed_position;
DROP TABLE manual_closed_position;
```

- היא ממירה נתונים אמיתיים, ולכן: לבדוק אותה קודם על DB זמני עם עותק של 5 השורות האמיתיות, ולהריץ `scripts/backup-db.sh` לפני
  ה-`./run.sh` שמריץ אותה על `portfolioboss`.

### 4.2 🟡 `domain`: מחיר ממוצע וסבבים פתוחים עם מכירות

- `HoldingHistory` עובר על העסקאות של כל סבב ומחזיק כמות ועלות של מה שמוחזק. **קנייה** מוסיפה לשתיהן. **מכירה** מוציאה מהעלות את החלק
  היחסי (כמות שנמכרה ÷ כמות מוחזקת); המכירה שמחזירה את הכמות לאפס מוציאה את כל מה שנשאר — כך סבב סגור יוצא מדויק, בלי עיגול.
  דוגמה: קנייה 10 ב-100, מכירה 5 ב-150, קנייה 10 ב-200, מכירה 15 ב-180 ← נמכרו 20 בעלות 3,000 (ממוצע 150), תמורה 3,450 (ממוצע 172.50),
  רווח +450 לפני עמלות. **עמלות:** עמלת קנייה נכנסת לאותו "מאגר" ויוצאת באותו יחס; עמלת מכירה כולה של השורה.
- `ClosedPosition` לכל סבב שיש בו מכירה (גם פתוח): כמה נמכר, עלות המניות שנמכרו (`null` אם לקנייה שלפני מכירה אין מחיר), תמורה,
  עמלות, כמה עוד מוחזק (0 בסבב שנסגר), וה-ids של העסקאות של הסבב. Avg buy = עלות שנמכרה ÷ כמות שנמכרה; הרווח והאחוז — כמו היום.
- `TradeFact` מקבל `id`, כדי שהשורה תדע אילו עסקאות שלה. מכירת יתר ומחיר חסר — כמו היום (אזהרה / "—").

### 4.3 🟡 `db`: `ManualPositionEntity` ועסקה של אחת משתיים

- `ManualPositionEntity` (symbol, currency, sector, note, `trades`) עם `tradeHistory()` כמו של `HoldingEntity`, ו-`ManualPositionRepository`
  (`@EntityGraph` על `trades`, כמו `findByAccountOrderById`).
- `TradeEntity`: `holding` או `manualPosition` — בנאי לכל אחד.
- `ManualClosedPositionEntity` / `Repository` של סשן 2 נמחקים.

### 4.4 🟡 `api`

- **קריאה:** `ClosedPositionResponse` לכל סבב עם מכירה, מהחזקות ומפוזיציות ידניות דרך אותו חישוב. שינויים מסשן 2 (שלא יצא החוצה, לכן מותר):
  `manualClosedPositionId` ← `manualPositionId`; `quantity` = מניות שנמכרו; חדשים בסוף: `remainingQuantity` (0 בסבב שנסגר), `trades`
  (`TradeResponse` של הסבב).
- **כתיבה** (`ManualPositionWriteController` / `ManualPositionWriteService`, במקום `ClosedPositionWrite*` של סשן 2), JSON בלבד, `@Valid`:
  - `POST /api/manual-positions` (201) — Symbol, Currency, Sector, Note, כמות, וקנייה ראשונה ומכירה ראשונה (תאריך, מחיר, עמלה לכל אחת;
    עמלה ריקה = ברירת המחדל לפקודה). כך לפוזיציה ידנית יש תמיד שורה בטבלה.
  - `PUT /api/manual-positions/{id}` (204) — Symbol, Currency, Sector, Note. `DELETE /api/manual-positions/{id}` (204) — עם העסקאות.
  - `POST /api/manual-positions/{id}/trades` (201 + `TradeResponse`) — עוד קנייה או מכירה.
  - `PUT` / `DELETE /api/trades/{id}` נשארים, ועובדים גם על עסקה של פוזיציה ידנית — עם כלל אחד: **אי אפשר למחוק (או להפוך לקנייה)
    את המכירה האחרונה של פוזיציה ידנית** (409: "delete the whole position instead"), אחרת היא נעלמת מהטבלה. **לערוך אותה כן אפשר**
    (עומרי, 03.10.2026): תאריך, כמות, מחיר, עמלה והערה — רק שינוי הצד ל-BUY נחסם. בדיקה לכל אחד מהשלושה.

### 4.5 🟢 בדיקות

`HoldingHistoryTest` / `ClosedPositionTest` (ממוצע, מכירה חלקית, קנייה אחרי מכירה חלקית, עמלות ביחס, מחיר חסר, מכירת יתר);
`PortfolioReadServiceTest` (שורה פתוחה עם `remainingQuantity`, `trades` של הסבב, פוזיציה ידנית); controller + service לפוזיציה ידנית ולכלל
המכירה האחרונה; `PortfolioControllerTest` (המפתחות החדשים).

---

## סשן 5 — UI: שורה שנפתחת, partial, פוזיציה ידנית

> **סטטוס (03.10.2026):** נבנה, עדיין לא committed (יחד עם סשן 4). `npm run build` ירוק; רינדור בצד השרת של האזור (שורה סגורה, partial,
> ידנית), של הפאנל של פוזיציה ידנית ושל טופס ההוספה. ⬜ בדיקה בדפדפן — בידי עומרי (`./run.sh` הבא מריץ את V3 על ה-DB האמיתי — לגבות קודם).
> מבנה: `TradeForm` / `TradesPanel` מקבלים `owner` (`TradeOwner`: החזקה או פוזיציה ידנית) במקום `holding` — אותו פאנל ב-Positions ובשורה
> הפתוחה; `TradesPanel` מייצא `TradeList` (בלי כפתורים = לקריאה בלבד, לשורה של החזקה). `ManualPositionForms.tsx` (`NewManualPositionForm`,
> `ManualPositionDetailsForm`) החליף את `ManualClosedPositionForm.tsx`; `ManualPositionPanel.tsx` חדש; `ExpandRowButton.tsx` משותף לשתי
> הטבלאות. השורה הפתוחה של פוזיציה ידנית נשארת פתוחה גם כשקנייה מוקדמת משנה את תאריך הקנייה שלה.

- **שורה שנפתחת** (חץ, כמו ב-Positions) ← העסקאות של הסבב: תאריך, צד, כמות, מחיר, עמלה, הערה.
  - החזקה: לקריאה בלבד, עם הסבר שעורכים ב-Positions.
  - פוזיציה ידנית: עריכה, מחיקה והוספה של עסקאות (`TradeForm` מוכלל — הוספה לפי בעלים, החזקה או פוזיציה ידנית), עריכת Symbol / Currency /
    Sector / Note, ו"Delete position". ה-✏️/🗑 שבשורה עוברים לשם.
- תגית **partial · still holding N** בשורה של סבב פתוח; כותרת Qty ← "Qty sold".
- **"+ Add closed position"** יוצר פוזיציה ידנית: Symbol, Currency, Sector, Note, Quantity, וקנייה ומכירה (תאריך, מחיר, עמלה לכל אחת).
- ~~באג הקלדת תאריך ידנית~~ — עומרי בדק שוב (03.10.2026): אין באג, הכול בסדר.

---

## מחוץ לתחום (כרגע)

- **עמלת ברירת מחדל במטבע שאינו USD נשארת כמו שהיא** (עומרי, 03.10.2026): הכלל בדולרים ונשמר במטבע של העסקה — ב-9988.HK נרשמו
  10 HK$ (כ-1.3$). מי שרוצה עמלה מדויקת מזין אותה.

- זיהוי כפילות בין שורה ידנית לסבב שנגזר מעסקאות של אותה מניה.
- מיון לפי עמודה בטבלת העסקאות הסגורות.
- FIFO / tax lots (נבחר מחיר ממוצע, החלטה 9).
- קניות של פוזיציה ידנית שאחרי המכירה האחרונה שלה (סבב שאין בו מכירה) לא מופיעות בטבלה — רק כשיימכרו.
