#!/bin/bash
GRADLE_FILE="app/build.gradle"

if [ ! -f "$GRADLE_FILE" ]; then
    echo "Error: $GRADLE_FILE not found!"
    exit 1
fi

# Extract current versionCode
CURRENT_CODE=$(grep "versionCode =" "$GRADLE_FILE" | awk '{print $3}')
if [ -z "$CURRENT_CODE" ]; then
    echo "Error: Could not find versionCode in $GRADLE_FILE"
    exit 1
fi

# Increment versionCode
NEW_CODE=$((CURRENT_CODE + 1))

# Extract current versionName
CURRENT_VERSION=$(grep "versionName =" "$GRADLE_FILE" | awk -F'"' '{print $2}')
if [ -z "$CURRENT_VERSION" ]; then
    echo "Error: Could not find versionName in $GRADLE_FILE"
    exit 1
fi

# Increment versionName (assuming format X.Y)
BASE=$(echo $CURRENT_VERSION | cut -d. -f1)
PATCH=$(echo $CURRENT_VERSION | cut -d. -f2)
NEW_PATCH=$((PATCH + 1))
NEW_VERSION="$BASE.$NEW_PATCH"

# Update build.gradle
sed -i "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$GRADLE_FILE"
sed -i "s/versionName = \"$CURRENT_VERSION\"/versionName = \"$NEW_VERSION\"/" "$GRADLE_FILE"

echo "Bumped Gradle version: $CURRENT_VERSION ($CURRENT_CODE) -> $NEW_VERSION ($NEW_CODE)"
