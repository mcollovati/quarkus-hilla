#!/bin/bash

# GA Release Notes Generator
# Combines GitHub pre-release notes into a single GA release note using AI
#
# Requirements:
# - gh CLI installed and authenticated (run: gh auth login)
# - OpenAI API key set as environment variable OPENAI_API_KEY
#   Get your API key from: https://platform.openai.com/api-keys
# - curl and jq installed
#
# Usage: ./generate_release_notes.sh <repo> <major.minor> [output_file] [--prompt-only]
# Example: ./generate_release_notes.sh owner/repo 2.1 release_notes.md
# Example (prompt only): ./generate_release_notes.sh owner/repo 2.1 --prompt-only

set -e

# Check arguments
if [ $# -lt 2 ] || [ $# -gt 4 ]; then
    echo "Usage: $0 <repo> <major.minor> [output_file] [--prompt-only]"
    echo "Example: $0 owner/repo 2.1 release_notes.md"
    echo "Example (prompt only): $0 owner/repo 2.1 --prompt-only"
    exit 1
fi

REPO="$1"
VERSION="$2"
OUTPUT_FILE=""
PROMPT_ONLY=false

# Parse arguments
for arg in "$@"; do
    case $arg in
        --prompt-only)
            PROMPT_ONLY=true
            shift
            ;;
        *)
            ;;
    esac
done

# Set output file if provided and not --prompt-only
if [ $# -ge 3 ] && [ "$3" != "--prompt-only" ]; then
    OUTPUT_FILE="$3"
fi

# Validate version format
if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+$ ]]; then
    echo "Error: Version must be in MAJOR.MINOR format (e.g., 2.1)"
    exit 1
fi

# Check dependencies
command -v gh >/dev/null 2>&1 || { echo "Error: gh CLI is required but not installed"; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "Error: curl is required but not installed"; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "Error: jq is required but not installed"; exit 1; }

# Check OpenAI API key only if not in prompt-only mode
if [ "$PROMPT_ONLY" = false ]; then
    if [ -z "$OPENAI_API_KEY" ]; then
        echo "Error: OPENAI_API_KEY environment variable is not set"
        echo "Get your API key from: https://platform.openai.com/api-keys"
        echo "Then run: export OPENAI_API_KEY='your-api-key-here'"
        echo ""
        echo "Alternatively, use --prompt-only flag to just display the AI prompt"
        exit 1
    fi
fi

echo "Fetching pre-releases for $REPO with version pattern $VERSION.0-*..."

# Fetch pre-releases matching the version pattern
PRERELEASE_TAGS=$(gh release list --repo "$REPO" --json tagName,isPrerelease --limit 100 -q '.[] | select( (.tagName | startswith("'${VERSION}'.0")) and .isPrerelease == true)' | jq -r '.tagName')

if [ -z "$PRERELEASE_TAGS" ]; then
    echo "No pre-releases found matching pattern ${VERSION}.0-*"
    exit 1
fi

echo "Found pre-releases:"
echo "$PRERELEASE_TAGS"

# Combine all pre-release notes
COMBINED_NOTES=""
for TAG in $PRERELEASE_TAGS; do
    echo "Fetching details for $TAG..."
    RELEASE_DATA=$(gh release view "$TAG" --repo "$REPO" --json name,body)
    NAME=$(echo "$RELEASE_DATA" | jq -r '.name')
    BODY=$(echo "$RELEASE_DATA" | jq -r '.body')

    COMBINED_NOTES="${COMBINED_NOTES}"$'\n\n'"## $TAG - $NAME"$'\n'"$BODY"
done

if [ -z "$COMBINED_NOTES" ]; then
    echo "No release notes content found in pre-releases"
    exit 1
fi

# AI prompt for processing release notes
AI_PROMPT="You are a technical writer tasked with creating a comprehensive release notes document for a GA (General Availability) release.

I will provide you with multiple pre-release notes from version ${VERSION}.0 release candidates. Your task is to:

1. Combine all the changes, features, and fixes into a single, well-organized release notes document
2. Remove duplicate entries and consolidate similar changes
3. Organize content into clear sections (e.g., New Features, Bug Fixes, Breaking Changes, etc.)
4. Use clear, professional language suitable for end users
5. Format the output as clean Markdown
6. Include a brief introduction mentioning this is the GA release of version ${VERSION}.0
7. Maintain chronological order where relevant and highlight the most important changes

Here are the pre-release notes to process:

${COMBINED_NOTES}

Generate comprehensive release notes for version ${VERSION}.0 GA release in GitHub compatible markdown format."

# Check if prompt-only mode
if [ "$PROMPT_ONLY" = true ]; then
    echo "=== AI PROMPT ==="
    echo "$AI_PROMPT"
    echo ""
    echo "=== END PROMPT ==="
    echo ""
    echo "Copy the prompt above and paste it into your preferred AI service (ChatGPT, Claude, etc.)"
    exit 0
fi

echo "Processing release notes with AI..."

# Call OpenAI API
RESPONSE=$(curl -s -X POST "https://api.openai.com/v1/chat/completions" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $OPENAI_API_KEY" \
    -d "{
        \"model\": \"gpt-4\",
        \"messages\": [
            {
                \"role\": \"user\",
                \"content\": $(echo "$AI_PROMPT" | jq -Rs .)
            }
        ],
        \"max_tokens\": 4000,
        \"temperature\": 0.3
    }")

# Check for API errors
if echo "$RESPONSE" | jq -e '.error' >/dev/null 2>&1; then
    echo "OpenAI API Error:"
    echo "$RESPONSE" | jq -r '.error.message'
    exit 1
fi

# Extract the generated release notes
RELEASE_NOTES=$(echo "$RESPONSE" | jq -r '.choices[0].message.content')

if [ -z "$RELEASE_NOTES" ] || [ "$RELEASE_NOTES" = "null" ]; then
    echo "Error: Failed to generate release notes"
    exit 1
fi

# Output the result
if [ -n "$OUTPUT_FILE" ]; then
    echo "$RELEASE_NOTES" > "$OUTPUT_FILE"
    echo "Release notes written to: $OUTPUT_FILE"
else
    echo "# Release Notes for $REPO v${VERSION}.0"
    echo ""
    echo "$RELEASE_NOTES"
fi

echo "✅ GA release notes generated successfully!"
