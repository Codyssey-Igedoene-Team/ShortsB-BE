# Git 작업 규칙

## 브랜치

| 브랜치 | 용도 |
| --- | --- |
| `main` | 배포할 코드 관리. `develop`의 변경 사항을 PR로 병합한 뒤 배포한다. |
| `develop` | 작업이 완료된 코드를 통합하고 배포 전 확인한다. |
| 작업 브랜치 | `develop`에서 분기하여 이슈 단위로 작업한다. |

작업 브랜치 이름: `유형/짧은-작업-요약/이슈번호`

- `feat`: 기능 추가. 예: `feat/add-login/12`
- `fix`: 버그 수정. 예: `fix/token-expiry/13`
- `refactor`: 리팩터링. 예: `refactor/user-service/14`
- `docs`: 문서 수정. 예: `docs/update-api/15`
- 작업 요약은 영문 소문자와 하이픈으로 짧게 작성하고, 이슈번호에는 `#`을 붙이지 않는다.

## 작업 순서

1. GitHub에서 이슈를 생성하고 작업 내용과 완료 조건을 작성한다.
2. 이슈 오른쪽 `Development → Create a branch`에서 규칙에 맞는 브랜치를 만든다. 기준은 반드시 최신 `develop`이어야 한다.
   - 이슈에서 만드는 브랜치는 기본 브랜치를 기준으로 생성되므로 기본 브랜치를 `develop`으로 설정하는 것을 권장한다.
   - 기본 브랜치를 `main`으로 유지하고 기준 선택이 불가능하면 저장소의 `Branches → New branch`에서 `Source: develop`으로 생성한다. 이후 PR의 `Development`에서 관련 이슈를 연결한다.
3. 로컬에서 원격 브랜치를 가져와 작업한다. 아래는 이슈 12번의 예시다.

   ```bash
   git fetch origin
   git switch --track origin/feat/add-login/12
   ```

4. 작업과 확인을 마친 뒤 변경 파일을 선택해 커밋하고 `git push`한다. 위 명령으로 추적 브랜치를 설정했으므로 추가 옵션은 필요 없다.
5. GitHub에서 `base: develop`, `compare: 작업 브랜치`로 PR을 생성하고 관련 이슈, 변경 내용, 확인 결과를 작성한다.
6. 작성자 외 리뷰어 1명 이상의 승인을 받고, 리뷰 내용을 반영한 뒤 `develop`에 병합한다. 병합 후 작업 브랜치는 삭제할 수 있다.
7. 배포할 작업이 완료되고 통합 확인이 끝나면 `base: main`, `compare: develop`으로 배포 PR을 생성한다. 승인 후 병합하고 `main`으로 배포한다. `develop`은 삭제하지 않는다.

`main`, `develop`에는 직접 push하지 않고 PR을 통해 변경한다.

## GitHub 저장소 설정

관리자 권한으로 다음을 설정한다. 아래는 classic branch protection 기준이다.

1. **브랜치와 이슈**: `main`, `develop`을 준비하고 `Settings → General → Features → Issues`를 활성화한다. `General → Default branch`는 `develop`을 권장한다. 기본 브랜치와 배포 브랜치는 별개다.
2. **보호 규칙**: `Settings → Branches → Add classic branch protection rule`에서 `main`, `develop` 각각에 규칙을 생성한다.
   - `Require a pull request before merging` 활성화.
   - `Require approvals` 활성화, 승인 수 `1` 설정.
   - `Dismiss stale pull request approvals when new commits are pushed` 활성화: 코드 변경 후 다시 승인받는다.
   - `Require conversation resolution before merging` 활성화.
   - `Do not allow bypassing the above settings` 활성화. PR 우회 허용은 추가하지 않는다.
   - `Allow force pushes`, `Allow deletions` 비활성화.
3. **리뷰 권한**: `Settings → Collaborators and teams`에서 리뷰할 팀원에게 `Write` 이상 권한을 부여한다. 작성자 본인의 승인은 필수 승인으로 사용할 수 없다.
4. **병합 방식**: `Settings → General → Pull Requests`에서 `Allow merge commits`를 활성화하고, 특히 반복되는 `develop → main` 병합에는 `Create a merge commit`을 사용한다. 자동 브랜치 삭제는 필수가 아니며 `develop`은 유지한다.
5. **템플릿**: 아래 두 파일을 원격 저장소의 **기본 브랜치**에 반영한다. 별도 템플릿 등록 설정은 필요 없다.
   - `.github/ISSUE_TEMPLATE/task.md`: `Issues → New issue`에서 선택하면 본문이 채워진다.
   - `.github/pull_request_template.md`: 새 PR 작성 시 본문에 자동으로 표시된다.
6. **배포**: 자동 배포를 사용한다면 배포 도구의 실행 대상을 `main`으로 지정한다. GitHub Actions는 `push` 이벤트의 `branches: [main]`을 사용한다. 브랜치 보호만으로 배포가 구성되지는 않는다.

보호 규칙은 공개 저장소에서 GitHub Free로 사용할 수 있다. 조직의 비공개 저장소에는 GitHub Team 또는 Enterprise가 필요하다.

브랜치 이름과 `main` PR의 출발 브랜치가 `develop`인지 여부는 위 보호 규칙만으로 강제되지 않으므로 리뷰에서 확인한다. 자동 강제가 필요하면 별도 PR 검증을 구성한다. CI가 준비되면 해당 검사에 `Require status checks to pass before merging`을 설정한다.

이슈 자동 종료용 `Closes #번호`는 기본 브랜치 대상 PR에서만 동작한다. 기본 브랜치가 `develop`이면 작업 PR에 사용할 수 있고, `main`이면 `develop` 병합 시 이슈를 수동 종료하거나 배포 PR에 종료할 이슈를 명시한다.

## 참고

- [브랜치 보호 설정](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/managing-a-branch-protection-rule)
- [이슈에서 브랜치 생성](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/creating-a-branch-for-an-issue)
- [이슈 템플릿 설정](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/configuring-issue-templates-for-your-repository)
- [PR 템플릿 설정](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/creating-a-pull-request-template-for-your-repository)
- [PR과 이슈 연결 및 자동 종료](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue)
