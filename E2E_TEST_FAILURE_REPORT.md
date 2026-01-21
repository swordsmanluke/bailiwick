# E2E Test Failure Report

**Date:** 2026-01-20
**Branch:** polecat/toast-mknarbtm
**Status:** COMPILATION FAILURE - Tests cannot run

## Summary

The E2E tests fail to compile because they reference UI elements that were removed in the Feed UI redesign (commit `178fbb5`).

## Compilation Errors

| File | Error Count |
|------|-------------|
| `ContactCircleNavigationTest.kt` | 13 errors |
| `NavigationFlowTest.kt` | 8 errors |

## Missing UI Element References

The following UI elements were removed but are still referenced in tests:

| Element ID | Former Location | References |
|------------|-----------------|------------|
| `btn_add_subscription` | User filter bar | 9 |
| `btn_refresh` | Header | 2 |
| `txt_peer` | Header | 1 |
| `txt_filter_label` | Circle filter bar | 2 |
| `list_users` | User filter list | 2 |

## Affected Test Methods

### NavigationFlowTest.kt (8 methods)
- `feedScreen_isDisplayedAfterLogin` (line 75)
- `feedScreen_addConnectionButtonNavigatesToConnectScreen` (line 89)
- `connectScreen_introduceYourselfNavigatesToIntroduceSelfScreen` (line 111)
- `connectScreen_scanIntroductionNavigatesToAcceptIntroductionScreen` (line 131)
- `connectScreen_backNavigatesToFeed` (line 151)
- `navigationFlow_feedToConnectToIntroduceAndBack` (line 287)
- `feedScreen_circleFilterIsInteractive` (line 323)
- `systemBackButton_fromConnectReturnToFeed` (line 358)

### ContactCircleNavigationTest.kt (9 methods)
- `contactScreen_hasRequiredElements` (line 69)
- `contactScreen_backButtonReturnsToFeed` (line 86)
- `feedScreen_userFilterListExists` (line 116)
- `navigationFlow_multipleScreensDeepNavigation` (lines 153, 186)
- `navigationFlow_rapidNavigationDoesNotCrash` (line 203)
- `feedScreen_allMainElementsVisible` (lines 229, 230, 233, 237, 238)
- `connectScreen_allElementsVisible` (line 295)
- `introduceSelfScreen_qrCodeVisible` (line 308)

## Recommended Actions

1. **Update E2E tests for Feed UI redesign**
   - Replace removed element IDs with new ones:
     - `btn_refresh` → `header_bar`
     - `txt_peer` → removed (no replacement needed)
     - `txt_filter_label` → `header_bar`
     - `list_users` → removed (no replacement)
     - `btn_add_subscription` → `btn_add_circle` (different destination)
   - Add tests for new elements: `layout_user_identity`, `img_header_avatar`, `txt_user_name`

2. **Review Connect screen navigation**
   - The `btn_add_subscription` button was removed from Feed
   - Tests relying on Feed → Connect navigation need alternative paths or removal
