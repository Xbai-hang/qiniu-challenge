import { computed, reactive, readonly } from 'vue'
import { getCalendarSpaces, type CalendarSpace } from '../api'

type WorkspaceState = {
  spaces: CalendarSpace[]
  selectedSpaceId: number | null
  isLoading: boolean
  error: string
}

const state = reactive<WorkspaceState>({
  spaces: [],
  selectedSpaceId: null,
  isLoading: false,
  error: '',
})

const currentSpace = computed(
  () => state.spaces.find((space) => space.id === state.selectedSpaceId) ?? null,
)

let loadPromise: Promise<CalendarSpace[]> | null = null

export function useWorkspaceStore() {
  async function loadSpaces(options: { force?: boolean } = {}) {
    if (loadPromise && !options.force) {
      return loadPromise
    }

    state.isLoading = true
    state.error = ''
    loadPromise = getCalendarSpaces({ showErrorMessage: false })
      .then((spaces) => {
        state.spaces = spaces
        if (!spaces.some((space) => space.id === state.selectedSpaceId)) {
          state.selectedSpaceId = spaces[0]?.id ?? null
        }
        return spaces
      })
      .catch((error) => {
        state.spaces = []
        state.selectedSpaceId = null
        state.error = error instanceof Error ? error.message : '空间加载失败'
        throw error
      })
      .finally(() => {
        state.isLoading = false
        loadPromise = null
      })

    return loadPromise
  }

  function selectSpace(spaceId: number | null) {
    state.selectedSpaceId = spaceId
  }

  function resetWorkspace() {
    state.spaces = []
    state.selectedSpaceId = null
    state.error = ''
    state.isLoading = false
    loadPromise = null
  }

  return {
    state: readonly(state),
    currentSpace,
    loadSpaces,
    selectSpace,
    resetWorkspace,
  }
}
