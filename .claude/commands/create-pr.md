---
description: Check the current feature branch, fill in the PR template, and open a pull request to main after approval
---

Open a pull request for the current branch into `main`, following `CLAUDE.md` and `.github/pull_request_template.md`.

1. **Check the branch.** Stop and tell me if any of these fail:
   - The branch is not `main`, and its name follows `feature/F-xx-…`, `fix/F-xx-…` or `chore/<topic>`.
   - `git status` is clean. If not, list the uncommitted files and ask whether to commit them first. Never commit without my answer.
   - Every commit in `git log main..HEAD` is a Conventional Commit (`feat(scope): …`, `fix(scope): …`, `test: …`, `docs: …`, `chore: …`).
   - No secrets, `.env` files, build output (`target/`, `dist/`, `node_modules/`), or real personal data in `git diff main...HEAD --stat` and the diff.

2. **Run the checks** and report each result:
   - `mvn -pl backend com.spotify.fmt:fmt-maven-plugin:check` and `mvn -pl backend verify`
   - `cd frontend && npm run lint && npm run typecheck && npm test`
   - End-to-end tests only if I ask. The e2e "clean state" step soft-deletes every subscription in the database it points at, so never run it against my development stack. Use a separate Compose project (`docker compose -p subsense-e2e …`) with its own ports and volume.

   If a check fails, stop and show the failure. Never skip, delete or weaken a test to get to green.

3. **Gather the context:**
   - The feature ID from the branch name, the plan in `docs/features/F-xx-*.md`, and the matching section of `SPEC.md`.
   - `git log main..HEAD` and `git diff main...HEAD --stat`.
   - The `/review-pr` findings from this conversation. If `/review-pr` has not been run for the latest commit, ask whether to run it first.

4. **Write the PR.**
   - **Title:** one Conventional Commit line, at most 72 characters, ending with the feature ID, e.g. `feat(rollover): daily renewal rollover with month-end handling (F-04)`.
   - **Body:** fill in every section of `.github/pull_request_template.md`:
     - **Summary:** what changed and why, in two or three sentences, with the feature and plan links.
     - **Changes:** one bullet per user-visible behaviour, then schema, API, config and docs changes.
     - **How it was tested:** tick only what was actually run in step 2 or earlier in this conversation, with test counts and the Cucumber tags.
     - **AI assistance:** tick what applies and add a line of detail for each.
     - **AI review findings:** each `/review-pr` finding and what was done about it (fixed in which commit, accepted trade-off, or deferred).
     - **Checklist:** tick only items that are true. Leave the others unticked and say why.
     - **Screenshots:** list which UI screens changed, so I can attach images. Don't invent images.
   - Save the body to `$(git rev-parse --git-dir)/PR_BODY.md`. That path is never committed.

5. **Show me the title and body, and stop.** Ask whether to push the branch and open the PR, and whether it should be a draft. Do not push until I say yes.

6. **After I approve:**
   - Push with `git push -u origin <branch>`. Never push to `main` and never force-push.
   - If `gh auth status` shows I'm signed in, run `gh pr create --base main --head <branch> --title "<title>" --body-file <body file>`, adding `--draft` if I asked for a draft. Then show the PR link.
   - If `gh` is not signed in, don't sign in for me. Give me the compare link (`https://github.com/<owner>/<repo>/compare/main...<branch>?expand=1`, built from `git remote get-url origin`) and the body file path to paste from, and mention `gh auth login` for next time.

Never merge the PR, enable auto-merge, request reviewers, or change branch protection or CI settings unless I ask.
