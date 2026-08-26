package com.raulshma.jellyplay.core.ui.viewmodel

object StateManagementConventions {

    const val PREFERRED_PATTERN = """
    # JellyPlay State Management Conventions
    
    ## Preferred: StateFlow + UiState Data Class
    
    All NEW ViewModels should use a single `StateFlow<UiState>` pattern:
    
    ```kotlin
    @Immutable
    data class MyUiState(
        val items: List<Item> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    )
    
    // (pre-KMP shape: HiltViewModel + Inject constructor)
    class MyViewModel(...) : JellyPlayViewModel() {
        private val _uiState = stateFlow(MyUiState())
        val uiState: StateFlow<MyUiState> = _uiState
        
        fun loadData() {
            launch {
                _uiState.update { it.copy(isLoading = true) }
                repository.getItems()
                    .onSuccess { _uiState.update { it.copy(items = it, isLoading = false) } }
                    .onFailure { _uiState.update { it.copy(error = it.message, isLoading = false) } }
            }
        }
    }
    ```
    
    ## Collect in Screens
    
    ```kotlin
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(lifecycleOwner = lifecycleOwner)
    ```
    
    ## One-shot Events
    
    Use `Channel` for one-shot events like navigation or showing toasts:
    
    ```kotlin
    private val _events = Channel<MyEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    ```
    
    ## Why StateFlow + UiState over individual composeState?
    
    - Atomic state snapshots (no intermediate inconsistent states)
    - Easier to test (single object to assert against)
    - Better for state restoration (single object to serialize)
    - Clearer state transitions in debug tools
    - Supports `.update {}` for thread-safe mutations
    """
}
