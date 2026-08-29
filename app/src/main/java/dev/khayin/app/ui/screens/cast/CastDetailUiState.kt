package dev.khayin.app.ui.screens.cast

import dev.khayin.app.domain.model.PersonDetail

sealed interface CastDetailUiState {
    data object Loading : CastDetailUiState
    data class Success(val personDetail: PersonDetail) : CastDetailUiState
    data class Error(val message: String) : CastDetailUiState
}
