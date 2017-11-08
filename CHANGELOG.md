# Change Log

## 1.0.0 - 2017-11-11
### Added
- [SVN] Subversion support
- [Common] "Rollback to here" command to a context menu that rollbacks the working tree (copy) to a previous commit (revision), but does not make a commit.
- [Common] Staging one of conflicted versions (e.g., mine,rXX,rYY in SVN; ours and theris in Git) accepts that version
- [Common] Visual diff between conflicted versions

### Changed
- [Git] No need to unstage to discard changes
- [Git] When Pull results in conflicts, do not reset hard (stay in a conflicted state). When conflicts occur, ours and theirs versions are created in the same directory (e.g., test.ktr -> test.ktr.ours and test.ktr.theirs)
- [Common] Rename "Git Project" to "Project" b/c it now supports SVN

### Fixed
- Fix a bug that deleted files cannot be staged for a commit

## 0.7.3 - 2017-10-17
### Fixed
- The layout of CloneRepositoryDialog
- Ignore parse error when typing an url
- A bug that a remote branch cannot be checked-out
- A bug that deleting a branch deletes a tag with the same name

## 0.7.2 - 2017-09-28
### Fixed
- Fix a typo bug that tries to push instead of pull when the cached credential does not get authenticated

## 0.7.1 - 2017-09-25
### Fixed
- Fix the bug that visual diff does not work when WORKINGTREE or HEAD is compared

## 0.7.0 - 2017-09-19
### Added
- Tagging feature
- Menuitems to push specific branch/tag
- A context menuitem "Reset to this commit"

### Changed
- To avoid accidental loss of work, abort merge/pull if one or more tabs have unsaved changes
- Do not merge (and pull) when the working tree is dirty

## 0.6.1 - 2017-09-05
### Added
- Ask to add a repository when no repository
- Restore Stage/Unstage menuitems in the context menu
- Show a error msg when trying to open/visualdiff a non-Kettle file

### Changed
- Switching to Git perspective does not trigger refresh, added refresh button for that
- Cache one pair of username and password (cleared when Spoon terminates)

### Removed
- Do not show texual diff upon selecting a revision

## 0.6.0 - 2017-08-30
### Changed
- Diff can now be between arbitrary two commits
- The two tables for unstaged and staged was merged into one table for changed files
- The list of changed files of past commits can now be shown
- An icon is attached to each changed file, representing ADDED, CHANGED, or REMOVED
- Texual/visual diff of past commits can now be obtained

## 0.5.1 - 2017-08-22
### Added
- Add version.xml and OSS_Licenses.md to the assemply zip

### Changed
- Save the repository information to metastore after cloneRepoWithUsernamePassword

### Fixed
- Show a confirmation dialog (after adding/deleting a repository)
- Prevent the texual diff from getting garbled (e.g., when Japanese)

## 0.5.0 - 2017-08-04
### Added
- A concept of "Git Repository" is introduced. No longer need to open a Kettle file in order to open the corresponding Git repository.

### Changed
- Move **Tools > Git** in the menu to the top toolbar in Git perspective

## 0.4.1 - 2017-07-20
### Fixed
- Fix an issue that visual diff of Kettle files in sub-directories is not shown

## 0.4.0 - 2017-07-13
### Added
- "Discard changes in working tree" to the context menu
- setAccelerator for "Data Integration" menu (CTRL+D)
- Visual diff

### Fixed
- Improve messages (e.g., "null" -> empty string)

## 0.3.2 - 2017-06-22
### Fixed
- Use Apache HTTP Client instead of Sun HTTP client. This fixes the communication with a remote repository via HTTP(S) in EE.

## 0.3.1 - 2017-06-13
### Added
- Japanese translation
- Users can clone a remote repository (from the menu Tools > Git)
- Users can checkout a particular commit (thru a context menu on a commit)
- Users can create / delete a branch
- Users can merge a branch into the current one

## 0.3.0 - 2017-06-05
### Added
- Users can switch between branches

## 0.2.2 - 2017-06-04
### Added
- Pull/push with username and password

## 0.2.1 - 2017-05-31
### Changed
- Do not show diff when WIP (work-in-progress) is selected for the sake of performance
- Remove the dependency on pdi-pur-plugin and copy the required classes

## 0.2.0 - 2017-05-28

### Added
- Show diff
- Support VFS (local)

## 0.1.0 - 2017-05-17

### Added
- Show directory, branch, remote (origin)
- Show commits
- Show unstaged changes and untracked files
- Show staged changes
- Users can edit the directory
- Users can edit the remote (origin only)
- Users can edit the commit message
- Users can edit the author name
- Users can initialize a git repository when not exists
- Users can stage and unstage files
- Users can commit
- Users can pull
- Users can push
