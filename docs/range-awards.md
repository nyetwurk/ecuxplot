# Awards

## Award Icons

- 🏆 (AWARD_OF): Overall FATS - Best FATS across all files
- 🥇 (AWARD_GF): Group FATS - Best FATS within a group (also used for best FATS in file)
- ⚡ (AWARD_OP): Overall Power - Best power across all files
- ⭐ (AWARD_GP): Group Power - Best power within a group (also used for best power in file)
- ⚠ (AWARD_POOR): Poor quality indicator

## File Awards (shown in parentheses after filename)

Files show awards based on their performance:

1. **Best FATS overall** (🏆): Shown when `isBestFATSFile = true`
2. **Best FATS in group** (🥇): Shown when `isBestFATSInGroup = true` (only if not best overall)
3. **Best power overall** (⚡): Shown when `isBestPowerFile = true`
4. **Best power in group** (⭐): Shown when `isBestPowerInGroup = true` (only if not best overall)

### Award Priority

- If a file has both overall and group awards, only the overall award is shown
- FATS and Power awards are independent - a file can have both

## Range Awards (shown after range info)

Ranges show awards in this order:

1. 🏆 Best FATS overall (if this range has the best FATS across all files)
   - OR 🥇 Best FATS in file (if this range is best FATS within its file and NOT overall best)
2. ⚡ Best power overall (if this range has the best power across all files)
   - OR ⭐ Best power in file (if this range is best power within its file and NOT overall best)
3. ⚠ Poor quality (if data is incomplete)

**Important:**

- If a range already has an overall FATS award (🏆), it won't show the "best FATS in file" award (🥇)
- If a range already has an overall power award (⚡), it won't show the "best power in file" award (⭐)
- The same icons are used for both group-level and file-level awards (🥇 for FATS, ⭐ for power)

## Group Awards (shown after group name)

Groups show which awards their files have:

- ⚡ Contains best power file
- 🏆 Contains best FATS file

## Award Determination Logic

### Overall Best Determination (in `analyzeFilePerformance` method)

- Find the best power across all files (find maximum power in any range)
- Find the best FATS across all files (find minimum FATS time in any range)
- Mark ranges that containt these bests
- Mark files that contain these best ranges
- Mark groups that contain these best ranges
- Store the filename and range index for each best

### Group Best Determination

Within each file:

- Find best power within the file (across all ranges in the file)
- Find best FATS within the file (across all ranges in the file)
- Mark ranges that have best power as best power in file (⭐ award)
- Mark ranges that have best FATS as best FATS in file (🥇 award)
- These use the same icons as group awards (⭐ for power, 🥇 for FATS)
- If a range already has an overall award, it won't show the file-level award

Within each group:

- Find best power within the group
- Find best FATS within the group
- Mark the files that contain these bests as best in gropu
- If a file already has an overall best, don't mark it as best in group

### Important Notes

- Awards are mutually exclusive within each category (can't be both overall and group best in FATS)
- Awards are independent between categories (can have best FATS AND best power)
- NEVER call setToolTipText(""), it produces empty tooltips that render as " "
  - Ensure ALL TOOLTIPS are either completely empty or contain valid HTML with no empty tags.
  - Ensure ALL TOOLTIPS do not contain ONLY whitespace or html breaks
- Share tooltip and award logic code, NO EXCEPTIONS, to guarantee tooltips and awards match perfectly.
