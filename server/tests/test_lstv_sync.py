"""Test unitario para la extracción y cruce de canales de LiveSoccerTV."""

import sports


def test_channel_map_and_normalization():
    assert sports._clean_channel_name("Canal 5 Televisa") == "Canal 5"
    assert sports._clean_channel_name("Azteca Deportes En Vivo") == "Azteca 7"
    assert sports._clean_channel_name("TUDN En Vivo") == "TUDN"
    assert sports._clean_channel_name("FOX One") == "Fox Sports"
    assert sports._clean_channel_name("FOX Mexico") == "Fox Sports"
    assert sports._clean_channel_name("FOX Sports 2 Mexico") == "Fox Sports 2"
    assert sports._clean_channel_name("ESPN Mexico") == "ESPN"
    assert sports._clean_channel_name("ESPN 2 Mexico") == "ESPN 2"
    assert sports._clean_channel_name("TNT Sports") == "TNT Sports"
    assert sports._clean_channel_name("Sky Sports Mexico") == "Sky Sports"
    assert sports._clean_channel_name("Layvtime") == "Layvtime"


def test_team_matching():
    home = "Guadalajara"
    away = "Atlas"
    assert sports.match_teams("Atlas vs Guadalajara", home, away)
    assert sports.match_teams("Chivas vs Atlas", home, away)
    assert not sports.match_teams("América vs Cruz Azul", home, away)

    # Alias europeos e internacionales
    assert sports.match_teams("Real Madrid vs Barcelona", "Barcelona", "Real Madrid")
    assert sports.match_teams("Man United vs Liverpool", "Manchester United", "Liverpool")
    assert sports.match_teams("Inter vs Parma", "Internazionale", "Parma")


def test_parse_lstv_html_sample():
    sample_html = """
    <tr id="5505207" class="matchrow" data-timer="FT" data-t1="1733" data-t2="1333" data-ko="2026-10-10 13:00:00">
        <td class="matchcol">
            <a href="/es/match/atlas-vs-guadalajara/" title="Atlas vs Guadalajara" id="g5505207">Atlas vs Guadalajara</a>
            <div class="channels-col">
                <a href="/es/channels/canal-5-mexico/">Canal 5 Televisa</a>,
                <a href="/es/channels/tudn/">TUDN</a>,
                <a href="/es/channels/layvtime/">Layvtime</a>
            </div>
        </td>
    </tr>
    """
    parsed = sports._parse_lstv_rows(sample_html)
    assert len(parsed) == 1
    match = parsed[0]
    assert match["title"] == "Atlas vs Guadalajara"
    assert "Canal 5" in match["channels"]
    assert "TUDN" in match["channels"]
    assert "Layvtime" in match["channels"]
