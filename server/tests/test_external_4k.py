"""4K público: etiquetas de audio para que el reproductor prefiera español."""

import app


def test_latino_labels_win_over_generic_spanish() -> None:
    assert app._four_k_language("Peli 2160p Dual Latino") == "Latino"
    assert app._four_k_language("Peli 2160p LATAM") == "Latino"
    assert app._four_k_language("Peli 2160p es-MX") == "Latino"


def test_spanish_is_detected_without_confusing_hindi() -> None:
    assert app._four_k_language("Peli 2160p Castellano") == "Castellano"
    assert app._four_k_language("Peli 2160p Español") == "Castellano"
    assert app._four_k_language("Peli 2160p Spanish") == "Castellano"
    assert app._four_k_language("Hindi-Tamil-Telugu + English") == ""
