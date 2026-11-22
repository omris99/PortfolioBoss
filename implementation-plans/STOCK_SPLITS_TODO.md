# Stock Splits — Session Implementation Plan

> **למי שקורא זאת בסשן חדש:** מדריך מימוש לפיצולי מניות. כשמניה עוברת פיצול, IB מעדכנת את כמות המניות (פי 4 בפיצול של 1 ל-4),
> אבל העסקאות שהוקלדו ב-PortfolioBoss נשארות בכמות של לפני. לכן מופיע ליד האחזקה המשולש "the trades don't add up to IB's
> position", והכמות של כל משקיע לא נכונה. דוח ה-Flex של IB כבר מביא שורה על כל פיצול (Corporate Actions, נשמר מאז V18), ואנחנו
> לא משתמשים בה להחזקות.
>
> **הפתרון** (רעיון של עומרי, 22.11.2026): **תגית על המניה** — "עברה פיצול של 1 ל-4 בתאריך X" —, שנשמרת פעם אחת כשהסנכרון מזהה
> את הפיצול בדוח ה-Flex. **העסקאות נשארות כמו שהוקלדו**, ובכל חישוב עסקה מתאריך הפיצול או לפניו נספרת במניות של היום: הכמות
> כפול 4, המחיר חלקי 4.
>
> שייך לעיקרון 1 ב-[TODO.md](TODO.md) (IB הוא מקור האמת לכמות, והנתונים של כל אחזקה נכונים) ול-6 (מדידה כנה: המס והרווח של כל
> משקיע). נבנה על [HOLDING_DETAILS_TODO.md](HOLDING_DETAILS_TODO.md) (האזהרות, סשן 7), [INVESTORS_TODO.md](INVESTORS_TODO.md)
> (הכמות של כל משקיע) ו-[CAPITAL_GAINS_TAX_TODO.md](CAPITAL_GAINS_TAX_TODO.md) (החלטה 15, הפעולות התאגידיות במס).
>
> **הסמלים, הכמויות ופרטי החשבון לא נכתבים כאן.** הדוגמאות מומצאות.
>
> סיכון לכל פריט: 🟢 נמוך | 🟡 בינוני | 🔴 גבוה. **✅ ליד מספר הפריט = בוצע.**

---

## מה נמצא בבדיקה (22.11.2026, ב-DB האמיתי, קריאה בלבד)

- **פיצול באותו contract** (`FS`): **שורה אחת**. אותו contract, הכמות היא **המניות שנוספו** (למשל 30 מניות נוספות ל-10 בפיצול
  של 1 ל-4), בלי כסף, והתיאור `… SPLIT 4 FOR 1 (…)`.
- **איחוד שעבר ל-contract חדש** (`RS`): **שתי שורות**. מינוס כל המניות ב-contract הישן ופלוס המניות החדשות ב-contract החדש,
  בלי כסף, והתיאור `… SPLIT 1 FOR 10 (…)`.
- באחזקה שעברה את הפיצול: העסקאות שהוקלדו מסתכמות לכמות של לפני, ו-IB מדווח את הכמות כפול היחס. זה ה-`QUANTITY_MISMATCH`.
- **באג במס שהפיצול חשף:** `calculation.tax.SplitAdjustedFlexTrades` מכיר רק את הצורה של שתי השורות, ועל `FS` הניח שהוא נכתב
  אותו דבר. בפועל `FS` הוא שורה אחת, ולכן הפעולה לא נעקבת ו**כל המכירות של המניה** מסומנות `CORPORATE_ACTION_NOT_SUPPORTED`
  מאז הסנכרון שהוריד את השורה. ימי הסליקה של המכירות האלה מוצגים בעמוד Taxes כ"לא ניתן לחשב", ואף אחד מהם לא אושר.

---

## החלטות שהתקבלו (22.11.2026)

1. **תגית על המניה, והעסקאות לא משתנות** (עומרי). הפיצול נשמר בטבלה משלו. עסקה שהוקלדה נשמרת כמו שהוקלדה, כמו באישור של IB
   מיום העסקה, והתרגום למניות של היום קורה רק בחישוב.
   - **נדחה: לשנות את העסקאות השמורות בסנכרון.** אפשר היה למנוע הכפלה כפולה, אבל:
     - עסקה ישנה שתוקלד אחר כך (מתאריך לפני הפיצול) הייתה צריכה להיות מוקלדת כבר במספרים של היום, או מומרת בטופס. ברשימה היה
       מופיע 40 ליד עסקה שהוקלד בה 10.
     - איחוד של 1 ל-10 הופך 7 מניות ל-0.7, ועסקה מקבלת רק מספר שלם של מניות.
     - הסנכרון לא כותב היום אף עסקה. רק שירותי הכתיבה כותבים אותן.
2. **כל פיצול נשמר פעם אחת, לפי המספר ש-IB נותן לפעולה** (`actionID`, `UNIQUE` בטבלה). ה-Flex מוריד בכל סנכרון את השבוע האחרון
   מחדש, אז אותה שורה מגיעה שוב ושוב. היא מוצאת את הפיצול כבר שמור, ושום דבר לא קורה. בלי סכנה של פי 16.
3. **היחס נשמר רק כששני מקורות מסכימים:**
   - **המילים של IB:** `SPLIT 4 FOR 1`, כלומר 4 מניות אחרי לכל מניה לפני.
   - **הכמויות:**
     - בשורה אחת: המניות שהיו לפני לפי עסקאות ה-Flex, כפול היחס, שוות למה שהיה לפני ועוד מה שנוסף (10 × 4 = 10 + 30).
     - בשתי שורות: מה שנוסף חלקי מה שנלקח, ועסקאות ה-Flex מסתכמות בדיוק למה שנלקח. זו הבדיקה של היום.

   לא מסכימים, אי אפשר לקרוא את המילים, או שיש בפעולה כסף (מניה חלקית שנפדתה)? לא נשמר, ומודפסת שורה. **לא מנחשים**, כמו
   במס היום.
4. **"לפני הפיצול" = בתאריך הפיצול או לפניו.** IB רושם את הפעולה בשעה 20:25 באותו יום (שעון ניו יורק), אחרי המסחר, אז עסקה
   מאותו יום נעשתה לפני הפיצול. עסקת Flex, שיש לה שעה: לפני השעה של IB, כמו היום. כמה פיצולים לאותה מניה מוכפלים זה בזה.
5. **כלל אחד להחזקות ולמס.** קריאת הפיצול מהשורות של IB יוצאת מ-`SplitAdjustedFlexTrades` (היום `FlexTradesAfterTheSplits`)
   ל-`SplitsInFlexCorporateActions`. הפיצולים נשמרים, וגם ההחזקות וגם המס קוראים את השמורים. כך המכירות שלא חושבו מחושבות שוב.
6. **רק מה שהשתנה מתורגם:** הכמות והמחיר. **העמלה לא משתנה.** העלות, התמורה והרווח הממומש יוצאים אותו דבר (פי 4 וחלקי 4
   מבטלים זה את זה). ממוצע המחיר של IB ומחירי הסגירה היומיים כבר במניות של היום: IB מתאימה אותם בעצמה.
7. **הפיצול חל רק על המניות שהיו בידיים ברגע הפיצול** (עומרי, 22.11.2026). IB לא מדווחת על פיצול במניה שכבר נמכרה כולה, כי
   היא כבר לא בתיק. לכן פוזיציה שנסגרה לפני הפיצול נשארת כמו שקרתה, בין שהמניה נקנתה שוב אחר כך ובין שלא. אחרת מניה שנקנתה שוב
   הייתה מתורגמת, ואותה פוזיציה סגורה בלי קנייה חוזרת לא.
   - **מה מתורגם:** כל העסקאות של הסבב שהיה פתוח ברגע הפיצול, מהפעם האחרונה שהכמות הגיעה ל-0 ועד הפיצול. כולל מכירה חלקית
     באותו סבב, שמוצגת במניות של היום ("16 ב-$100" במקום "4 ב-$400", אותו כסף): בלי זה הכמויות של הסבב לא היו מסתכמות לכמות של IB.
   - **לכל משקיע לבד**, כמו הפוזיציות הסגורות: אם שי מכר הכול לפני הפיצול, הפוזיציה הסגורה שלו נשארת כמו שקרתה.
   - **גם במס**, על עסקאות ה-Flex של החשבון: הסבב של החשבון שהיה פתוח ברגע הפיצול. סבב שנסגר לפניו לא משנה את ה-FIFO, כי כל
     הקניות שלו כבר נמכרו, ולכן מכירה ישנה נשארת בכמויות שבדוח של הפניקס.
8. **הפיצול מוצג ברשימת העסקאות שב-drawer של המניה** (עומרי, 22.11.2026), כשורה במקום שלו לפי התאריך, ולא בטבלת ה-Positions.

---

## השמות (עומרי אישר, 22.11.2026)

עומרי מאשר שמות **לפני תחילת עריכת הקוד**. המילה שלו, **"מתורגמת"**: עסקה מלפני פיצול מתורגמת בהתאם. "position period" ו-"split
adjusted" לא היו מובנים לו, ולכן לא נכנסו.

| מה | שם | איפה |
|---|---|---|
| פיצול אחד, כמו שנשמר: המניה לפני ואחרי, מתי, "4 for 1" | `StockSplit` (`sharesBeforeTheSplit`, `sharesAfterTheSplit`, `splitDay()`, `isBeforeTheSplit(…)`, `quantityAfterTheSplit(…)`, `priceAfterTheSplit(…)`, `keepsTheSameContract()`, `ratioInIbsWords()`) | `calculation/splits` |
| כל הפיצולים שבטבלה | `StoredStockSplits` (`splitsOfTheHolding(conId)`, `isStored(ibActionId)`) | `calculation/splits` |
| העסקאות שהקלדת, כמו שהן נספרות אחרי הפיצולים | `TradesAfterTheSplits` (`tradesAsTyped`, `allTrades()`, `translatedTrades()`) | `calculation/splits` |
| העסקאות של הפוזיציה הפתוחה (מהפעם האחרונה שלא הוחזקה אף מניה) | `HoldingHistory.openPositionTrades` | `calculation/portfolio` |
| מוצא את הפיצולים בשורות ה-Corporate Actions (החלטה 3) | `SplitsInFlexCorporateActions` (`splitsUnderstood()`, `splitsNotUnderstood()`), `SplitNotUnderstood`, `WhySplitNotUnderstood` | `calculation/tax` |
| עסקאות ה-Flex אחרי הפיצולים, למס (היה `SplitAdjustedFlexTrades`) | `FlexTradesAfterTheSplits` (`sharesHeldJustBefore(conId, moment)`) | `calculation/tax` |
| הטבלה | `stock_split`, `StockSplitEntity`, `StockSplitRepository` (`storedStockSplits()`) | `db/splits` |
| השלב בסנכרון ששומר פיצול חדש | `StockSplitDetectionService.storeNewSplits()` | `sync` |
| האחזקה | `HoldingEntity.tradesAfterTheSplits(stockSplits)`, `tradeHistory(stockSplits)`, `toPositionTrades(stockSplits)` | `db/portfolio` |
| ב-API | `HoldingResponse.stockSplits` (`StockSplitResponse`), `HoldingResponse.translatedTrades` (`TranslatedTradeResponse(tradeId, quantity, price)`) | `api/portfolio/response` |

**חוקי החבילות** (`ArchitectureTest`):
- `calculation.splits` משתמש ב-`calculation.portfolio`, ו-`calculation.tax` משתמש ב-`calculation.splits`.
  `calculation.portfolio` **לא** מכיר פיצולים: `HoldingHistory` מקבל עסקאות שכבר תורגמו.
- **מציאת הפיצולים ב-`calculation/tax` ולא ב-`calculation/splits`:** היא צריכה את `FlexTradesAfterTheSplits` של המס, בשביל המניות
  שהוחזקו לפני כל פעולה. `calculation/splits` לא יכול להשתמש ב-`tax`, כי זה היה מעגל.
- `db.splits` משתמש ב-`calculation.splits`, כמו `FlexCorporateActionEntity.toFlexReportCorporateAction()`.
- השלב בסנכרון ב-`sync`, ש-`SyncService` כבר שם. הוא קורא את `db.tax` (שורות ה-Flex) וכותב את `db.splits`.

---

## סשן 1 ✅ — הפיצולים: backend 🟡

> **מצב (22.11.2026):** נבנה ונבדק חי. 765 בדיקות עוברות (`mvn -q clean test`, כולל `ArchitectureTest`), וגם `npm run build`.
> V20 ב-DB האמיתי מאז ההרצה החיה של אותו יום (1.8), אחרי הגיבוי `portfolioboss-2026-11-22.sql.gz` (22 מ-22 טבלאות שוות).
>
> **מה השתנה מהתכנון:**
> - **המספרים אחרי הפיצול ב-API נשלחים על האחזקה** (`HoldingResponse.translatedTrades`), ולא בתוך `TradeResponse`. הסיבה: התרגום
>   של עסקה תלוי בשאר העסקאות שלה (באיזה סבב היא), ו-endpoint כתיבה מחזיר עסקה אחת לבד. `TradeResponse` נשאר מה שהוקלד.
> - **השלב רץ גם בהפעלה שלא קראה את TWS.** הוא קורא רק מה ששמור, וכך ההרצה הראשונה אחרי V20 שומרת את הפיצולים שכבר בדוחות. בלי זה
>   המס היה מפסיק לעקוב אחרי האיחוד הישן עד הסנכרון הבא עם TWS.
> - **במקום מחלקה חדשה לגבולות הסבבים,** `HoldingHistory` קיבל רכיב אחד, `openPositionTrades`: הוא כבר מחלק לסבבים.
> - **מי מחזיק מניה מהסריקה** (`RecommendationsReadService`) מחושב פעם אחת לכל קריאה, לפי האחזקות אחרי הפיצולים.

### 1.1 ✅ `V20__stock_splits.sql`
```
stock_split (id, ib_action_id TEXT NOT NULL UNIQUE, symbol TEXT NOT NULL, con_id_before_the_split INTEGER NOT NULL,
             con_id_after_the_split INTEGER NOT NULL, split_time TIMESTAMP NOT NULL,
             shares_before_the_split INTEGER NOT NULL, shares_after_the_split INTEGER NOT NULL, stored_at TIMESTAMPTZ NOT NULL)
```
- `shares_before_the_split` / `shares_after_the_split` הם `4 FOR 1` של IB (1 ו-4), שני מספרים שלמים ולא יחס עשרוני: 1 ל-3 אי אפשר
  לכתוב במדויק. כל אחד `CHECK (… > 0)`.
- `split_time` היא השעה של IB, כמו `flex_corporate_action.action_time`.
- פיצול באותו contract: שני ה-`con_id` שווים.

**לפני ה-`./run.sh` שמביא את V20: `scripts/backup-db.sh`**, ולבדוק את מספר השורות מול ה-DB, כמו בכל migration.

### 1.2 ✅ `calculation.tax.SplitsInFlexCorporateActions(corporateActions, flexTrades)` — טהור
- `splitsUnderstood()`: כל פעולה מסוג `FS` / `RS` מאחת משתי הצורות, שעוברת את שתי הבדיקות של החלטה 3. הצורות:
  - שורה אחת באותו contract (גם עם כמות שלילית: איחוד באותו contract);
  - שתי שורות באותו זמן, אחת שלוקחת ואחת שמוסיפה.
- `splitsNotUnderstood()`: השאר, כל אחד עם `WhySplitNotUnderstood`, בסדר הבדיקות:
  - `ROWS_OF_ANOTHER_KIND`;
  - `IT_BRINGS_MONEY`;
  - `NO_SPLIT_IN_IBS_WORDS`;
  - `SHARES_DONT_MATCH` (גם כשחסרה עסקה, וגם כשלא הוחזקה אף מניה).
- המניות שהוחזקו רגע לפני כל פעולה: `FlexTradesAfterTheSplits.sharesHeldJustBefore`, אחרי הפיצולים שהובנו לפניה.
- `StockSplit`: החישוב בדיוק של 16 ספרות (`MathContext.DECIMAL64`), כמו קודם.

### 1.3 ✅ השלב בסנכרון — `sync.StockSplitDetectionService.storeNewSplits()`
- רץ מ-`SyncService` מיד אחרי `downloadMissingFlexReports`, ולפני השערים והמס, בכל סנכרון שהגיע ל-TWS. רץ גם בהפעלה שלא קראה את
  TWS, כי הוא קורא רק מה ששמור.
- עובר על **כל** הפעולות השמורות ושומר כל פיצול שהובן ועוד לא שמור. לכן ההרצה הראשונה שומרת גם את שני הפיצולים שכבר בדוחות, בלי
  להוריד שוב את הדוח.
- שומר, ומדפיס `[db] stored a stock split: ABCD 4 for 1 on 2026-03-10` (עם `, into a new contract` כשהמניה עברה). כשאין פיצול חדש
  לא מודפס כלום.
- פיצול שלא הובן מודפס בכל סנכרון, כי הוא נבדק שוב: `[db] a stock split not understood: ABCD on 2026-03-10: …`. רק עבור `FS` /
  `RS`; spin-off או מיזוג לא מודפסים כאן.
- כשל מדפיס `[db error] could not store the stock splits: …` ולא מפיל את הסנכרון.
- **ידוע:** ההתראות נבדקות לפני הורדת ה-Flex (`PortfolioSyncedEvent`). בסנכרון שמביא פיצול חדש, `TRADES_DONT_MATCH_IB` עלולה להיפתח
  פעם אחת ולהיסגר בסנכרון הבא.

### 1.4 ✅ העסקאות אחרי הפיצולים — `TradesAfterTheSplits(tradesAsTyped, splitsOfTheHolding)`
- לכל משקיע לבד (החלטה 7): `HoldingHistory.of(העסקאות שלו מיום הפיצול ולפניו).openPositionTrades()` הן העסקאות שמתורגמות: הכמות
  כפול היחס, המחיר חלקי היחס. אותו כלל סבבים כמו בכל השאר. כמה פיצולים: כל אחד בתורו, לפי הסדר.
- `allTrades()` מחזיר את כל העסקאות לפי הסדר שניתן. `translatedTrades()` מחזיר רק את אלה שתורגמו, בשביל המסך.
- עסקה של סבב שנסגר לפני הפיצול לא נוגעת.
- `HoldingEntity.tradeHistory(stockSplits)` ו-`toPositionTrades(stockSplits)` **מקבלים את הפיצולים כפרמטר חובה**. כל מקום שקורא
  להם לא מתקמפל עד שהוא מעביר אותם, ככה אף אחד לא שוכח: `PortfolioReadService`, `AlertService` (`TRADES_DONT_MATCH_IB`),
  `RecommendationsReadService`, והמס של משקיע. כל הפיצולים נקראים בשאילתה אחת לכל קריאה.
- הפיצולים של אחזקה: אלה ש-`con_id_before = con_id_after = holding.conId`.
- מה זה מתקן: המשולש, הכמות והשווי של כל משקיע (והשארית של בעל החשבון), הכרטיסים בעמוד Investors, תקופת ההחזקה והמשקלות.
- פוזיציה ידנית לא מתורגמת: אין לה contract, והיא נמכרה לפני הסנכרון הראשון.

### 1.5 ✅ המס
- `FlexTradesAfterTheSplits(flexTrades, stockSplits, corporateActions)` (היה `SplitAdjustedFlexTrades`): מתרגם לפי הפיצולים
  השמורים, ורק את הסבב של החשבון שהיה פתוח ברגע הפיצול (החלטה 7; עד עכשיו תורגמה כל עסקה לפני הפעולה).
  `CORPORATE_ACTION_NOT_SUPPORTED` רק לפעולה שאין לה שורה ב-`stock_split`.
- המס של משקיע אחר, מהעסקאות שהוקלדו, דרך אותו `TradesAfterTheSplits`.
- `TypedTradeSettlementDays` לא משתנה: התאום ב-Flex נמצא לפי contract ויום, לא לפי כמות.
- **צפוי בהרצה החיה:** המכירות של 2026 במניה שעברה פיצול מחושבות שוב. השורה `our FIFO against IB's` נשארת כמו לפני השורה של
  הפיצול.

### 1.6 ✅ ה-API — רק מוסיפים מפתחות
- `HoldingResponse.stockSplits`: `StockSplitResponse(splitDay, sharesBeforeTheSplit, sharesAfterTheSplit)`, ורשימה ריקה כשאין
  פיצול.
- `HoldingResponse.translatedTrades`: `TranslatedTradeResponse(tradeId, quantity, price)` לכל עסקה שתורגמה. היא נמצאת לפי
  `tradeId`, וזה תקף גם לעסקאות של פוזיציה סגורה (`holdingId`).
- **`TradeResponse` נשאר מה שהוקלד.** טופס העריכה שולח רק אותו. אם ישלח את המספרים המתורגמים, הקריאה הבאה תכפיל שוב.
- `ui/src/types/portfolio.ts` עודכן באותו שינוי (`StockSplit`, `TranslatedTrade`).

### 1.7 ✅ בדיקות — ערכים מומצאים, **אף בדיקה לא קוראת ל-IB**
- `StockSplitTest`: 4 ל-1, מכירה שומרת על הסימן, 1 ל-3 ב-16 ספרות, עסקה מיום הפיצול לפניו ומהיום שאחריו לא, השעה של IB, היחס
  במילים של IB.
- `StoredStockSplitsTest`: לפי הזמן; של אחזקה רק מה ששמר על ה-contract שלה; `isStored`.
- `TradesAfterTheSplitsTest`:
  - עסקה לפני הפיצול, ביום הפיצול ואחריו;
  - פוזיציה שנמכרה כולה לפני הפיצול לא מתורגמת, גם כשהמניה נקנתה שוב;
  - מכירה חלקית בסבב הפתוח מתורגמת, והסבב מסתכם לכמות של IB;
  - שני משקיעים: אחד מכר הכול לפני הפיצול, השני החזיק;
  - שני פיצולים;
  - העמלה לא משתנה;
  - בלי פיצול הכול כמו שהוקלד.
- `SplitsInFlexCorporateActionsTest`:
  - שורה אחת, 10 מניות, 4 ל-1, 30 נוספות;
  - שתי שורות ל-contract חדש, 1 ל-10;
  - מילים וכמויות לא מסכימות;
  - עסקה חסרה;
  - מניה שלא הוחזקה;
  - מילים בלי "SPLIT n FOR m";
  - פעולה עם כסף;
  - שורות מסוג אחר;
  - spin-off לא מדווח;
  - פיצול שני נבדק מול מה שהראשון השאיר.
- `FlexTradesAfterTheSplitsTest` (היה `SplitAdjustedFlexTradesTest`):
  - איחוד ל-contract חדש;
  - פיצול באותו contract;
  - סבב שנמכר כולו לפני הפיצול לא מתורגם;
  - מכירה חלקית בסבב הפתוח מתורגמת;
  - פעולה בלי פיצול שמור לא נעקבת;
  - `sharesHeldJustBefore`.
- `HoldingHistoryTest`: `openPositionTrades`.
- `StockSplitDetectionServiceTest` (`@DataJpaTest`): נשמר; סנכרון שני לא מוסיף; פיצול שלא הובן לא נשמר; בלי פעולה כלום.
- `SyncServiceTest`: אחרי הדוחות ולפני המס; גם בהפעלה בלי TWS; כשל לא מפיל את הסנכרון.
- `PortfolioReadServiceTest`: אחרי פיצול אין משולש, לכל משקיע הכמות שלו, העסקאות כמו שהוקלדו והתרגום לידן, והפוזיציה שנמכרה כולה
  לפני הפיצול כמו שקרתה.
- `AlertServiceTest`: אין `TRADES_DONT_MATCH_IB`.
- `CapitalGainsTaxServiceTest`: העסקאות של משקיע מתורגמות דרך פיצול שמור; פיצול לא שמור לא נעקב; האיחוד השמור נעקב (עכשיו דרך
  השלב).
- `PortfolioControllerTest`: המפתחות החדשים.

### 1.8 ✅ ההרצה החיה (עומרי, 22.11.2026)
עברה: שני הפיצולים שכבר היו בדוחות נשמרו בהרצה הראשונה. אחד באותו contract, והאיחוד הישן `, into a new contract`.
1. גיבוי.
2. `./run.sh` עם TWS. אפשר גם `./run.sh 7599` בלי TWS: השלב רץ גם אז, אבל המס לא מודפס.
3. בקונסול: שתי שורות `[db] stored a stock split`, ובהרצה שאחריה אף אחת.
4. ב-`/api/portfolio`: האחזקה בלי משולש.
5. ב-`[tax]`: השנה של 2026 בלי "not computed … corporate action".

---

## סשן 2 — הפיצולים: UI 🟢

### 2.1 🟢 הפיצול ברשימת העסקאות (החלטה 8)
- ברשימת העסקאות שב-drawer של המניה (`TradesPanel`), שורה אפורה במקום שלה לפי התאריך, בין העסקאות שלפני ואחרי: "10.03.2026 ·
  Split 4 for 1 (from IB's report)". מידע ולא אזהרה, כמו `InfoMark`. בלי כפתורי עריכה ומחיקה: היא מ-IB.
- ריחוף מסביר: "Since 10.03.2026 IB counts 4 shares for each one before. The shares held then count ×4; a position closed before
  it stays as it was."
- לא בטבלת ה-Positions.

### 2.2 🟢 שורת העסקה
ברשימת העסקאות (`TradesPanel`, וגם בעסקאות של פוזיציה סגורה), מתחת לכמות ולמחיר שהוקלדו, ורק לעסקה שתורגמה: "= 40 at $100 after
the split", מ-`holding.translatedTrades` לפי `tradeId`.

### 2.3 🟡 הטופס (`TradeModal`)
- **תאריך בתאריך פיצול או לפניו:** מתחת לשדות: "This date is before the 4-for-1 split of 10.03.2026: type the shares and price as
  they were then."
- **מילוי מראש של אחזקה בלי עסקאות** (הכמות של IB וממוצע המחיר של IB, **במניות של היום**): כשהתאריך לפני פיצול, המספרים
  שמולאו מראש מומרים לאותו יום (חלקי 4 וכפול 4). אחרת העסקה תיספר פי 4 פעמיים. 🟡 זה המקום היחיד שה-UI מחשב פיצול בעצמו.

### 2.4 בדיקת דפדפן
צעדים מדויקים: מה ללחוץ, ומה הטקסט שאמור להופיע.

---

## מחוץ לתחום (כרגע)

- **איחוד שמעביר אחזקה פתוחה ל-contract חדש.** הסנכרון סוגר את האחזקה הישנה (`CLOSED_WITHOUT_SELL`) ופותח חדשה בלי עסקאות
  (`NO_TRADES_LOGGED`), כמו היום. הפיצול השמור כבר יודע את שני ה-contracts, אז סשן אחר יוכל לקשר ביניהם.
- **פיצול כשה-Flex לא מחובר** (בלי `FLEX_TOKEN`): אין תגית, והמשולש נשאר. רעיון לאחר כך: להזין פיצול ביד.
- פיצול עם כסף (מניה חלקית שנפדתה), spin-off ומיזוג: לא נעקבים, כמו במס היום.
