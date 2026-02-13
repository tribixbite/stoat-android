Build the Stoat Android app using the ARM64 build script. Pass arguments through: `debug` (default), `release`, `clean`, or combinations like `debug clean`.

Run: `./build-and-install.sh $ARGUMENTS`

After the build completes:
- Report BUILD SUCCESSFUL or FAILED with the relevant error
- If failed, analyze the error and suggest fixes
- Note the APK size and installation method used
