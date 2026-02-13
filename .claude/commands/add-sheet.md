Add a new bottom sheet to the Stoat Android app. The user will describe the sheet's purpose and contents.

Steps:
1. Create or modify files in `app/src/main/java/chat/stoat/sheets/`
2. Follow the existing bottom sheet pattern:
   - Use `ModalBottomSheet` with `rememberModalBottomSheetState`
   - Use `SheetButton` from `chat.stoat.composables.generic` for action items
   - Use `Icon` with `painterResource` for leading icons
   - Handle dismiss with coroutineScope and the sheet state
   - Check permissions using `Roles.permissionFor()` and `has PermissionBit.X`
3. Add the sheet trigger in the appropriate screen or context menu
4. Add string resources for all UI text
5. Add confirmation dialogs (`AlertDialog`) for destructive actions
6. Build with `./build-and-install.sh` to verify compilation

Icons available: Check `app/src/main/res/drawable/` for `icn_*.xml` and `ic_*.xml` files.
