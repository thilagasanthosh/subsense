---
description: Implement an approved SubSense feature plan using strict red-green-refactor TDD
argument-hint: <feature-id, e.g. F-05>
---

Implement feature $ARGUMENTS following the approved plan in `docs/features/$ARGUMENTS-*.md` and the rules in `CLAUDE.md`.

1. If no approved plan exists, stop and tell me to run `/plan-feature $ARGUMENTS` first.
2. Add the Cucumber scenarios for $ARGUMENTS (tagged `@$ARGUMENTS`) and confirm they fail.
3. Work through the plan's test list one behaviour at a time:
   - **Red:** write one failing test. Run it and show that it fails for the expected reason.
   - **Green:** write the minimum code to pass it. Run the tests.
   - **Refactor:** improve the code with all tests green.
4. After each completed behaviour, run `mvn com.spotify.fmt:fmt-maven-plugin:format` (backend) or Prettier (frontend) and the relevant test suite.
5. Never skip, delete or weaken a test to get to green. If a test seems wrong, stop and explain.
6. When the list is done, run the full checks (`mvn verify`, frontend lint/typecheck/test, `mvn -pl e2e verify -Dcucumber.filter.tags="@$ARGUMENTS"`).
7. Finish with a summary: behaviours implemented, tests added, anything deferred, and any deviation from the plan.
