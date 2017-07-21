# Change Log

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