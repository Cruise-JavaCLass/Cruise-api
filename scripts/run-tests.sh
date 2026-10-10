#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

MODULE="${1:-all}"
TEST_FILTER="${2:-}"

MVN_ARGS=()

case "$MODULE" in
    all)
        ;;
    common)
        MVN_ARGS+=("-pl" "cruise-common")
        ;;
    gateway)
        MVN_ARGS+=("-pl" "cruise-gateway")
        ;;
    auth)
        MVN_ARGS+=("-pl" "cruise-auth-service")
        ;;
    itinerary)
        MVN_ARGS+=("-pl" "cruise-itinerary-service")
        ;;
    activity)
        MVN_ARGS+=("-pl" "cruise-activity-service")
        ;;
    pos)
        MVN_ARGS+=("-pl" "cruise-pos-service")
        ;;
    *)
        MVN_ARGS+=("-pl" "$MODULE")
        ;;
esac

if [ -n "$TEST_FILTER" ]; then
    MVN_ARGS+=("-Dtest=$TEST_FILTER")
fi

echo "=========================================================="
echo " Cruise API Test Runner (POSIX)"
echo " Command: ./mvnw test ${MVN_ARGS[*]}"
echo " Target Module: $MODULE"
echo "=========================================================="

./mvnw test "${MVN_ARGS[@]}"
