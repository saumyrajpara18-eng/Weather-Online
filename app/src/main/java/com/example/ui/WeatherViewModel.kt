package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.City
import com.example.data.model.TemperatureUnit
import com.example.data.model.WeatherReport
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Success(
        val report: WeatherReport,
        val isRefreshing: Boolean = false
    ) : WeatherUiState
    data class Error(
        val message: String,
        val previousReport: WeatherReport? = null
    ) : WeatherUiState
}

data class WeatherScreenState(
    val uiState: WeatherUiState = WeatherUiState.Loading,
    val selectedCity: City = City("London", "United Kingdom", "England", 51.5074, -0.1278),
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val searchQuery: String = "",
    val searchResults: List<City> = emptyList(),
    val isSearching: Boolean = false,
    val popularCities: List<City> = emptyList(),
    val searchError: String? = null
)

class WeatherViewModel(
    private val repository: WeatherRepository = WeatherRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(
        WeatherScreenState(
            popularCities = repository.popularCities,
            selectedCity = repository.popularCities.first()
        )
    )
    val state: StateFlow<WeatherScreenState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadWeather(city = _state.value.selectedCity)
    }

    fun loadWeather(city: City? = null, isRefresh: Boolean = false) {
        val targetCity = city ?: _state.value.selectedCity
        val targetUnit = _state.value.unit

        viewModelScope.launch {
            val currentReport = (_state.value.uiState as? WeatherUiState.Success)?.report

            if (isRefresh && currentReport != null) {
                _state.update {
                    it.copy(
                        selectedCity = targetCity,
                        uiState = WeatherUiState.Success(currentReport, isRefreshing = true)
                    )
                }
            } else if (currentReport == null || targetCity != _state.value.selectedCity) {
                _state.update {
                    it.copy(
                        selectedCity = targetCity,
                        uiState = WeatherUiState.Loading
                    )
                }
            }

            val result = repository.getWeatherReport(targetCity, targetUnit)
            result.fold(
                onSuccess = { report ->
                    _state.update {
                        it.copy(
                            selectedCity = targetCity,
                            uiState = WeatherUiState.Success(report, isRefreshing = false)
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            uiState = WeatherUiState.Error(
                                message = throwable.localizedMessage ?: "Failed to load weather data",
                                previousReport = currentReport
                            )
                        )
                    }
                }
            )
        }
    }

    fun toggleUnit() {
        val newUnit = if (_state.value.unit == TemperatureUnit.CELSIUS) {
            TemperatureUnit.FAHRENHEIT
        } else {
            TemperatureUnit.CELSIUS
        }
        _state.update { it.copy(unit = newUnit) }
        loadWeather(_state.value.selectedCity)
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query, searchError = null) }
        searchJob?.cancel()

        if (query.trim().length < 2) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(350) // Debounce typing
            _state.update { it.copy(isSearching = true) }
            val result = repository.searchCities(query)
            result.fold(
                onSuccess = { cities ->
                    _state.update {
                        it.copy(
                            searchResults = cities,
                            isSearching = false,
                            searchError = if (cities.isEmpty()) "No cities found for \"$query\"" else null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            searchResults = emptyList(),
                            isSearching = false,
                            searchError = error.localizedMessage ?: "Error searching cities"
                        )
                    }
                }
            )
        }
    }

    fun selectCity(city: City) {
        _state.update {
            it.copy(
                selectedCity = city,
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                searchError = null
            )
        }
        loadWeather(city)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                searchError = null
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WeatherViewModel() as T
            }
        }
    }
}
