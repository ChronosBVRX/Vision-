"""Vision+ Fiesta 4.0: bucles nuevos, economía y anti-regresiones."""

from __future__ import annotations

import json
import math

import fiesta
import fiesta_content
import games


def _room(game: str, rounds: int = 2, players: int = 3, family: bool = True,
          solo: bool = False, code: str = "ABC234"):
    room = games.create_room(
        code, "Pantalla", "host", "", [],
        game=game, rounds=rounds, mode="display", countdown_seconds=0,
        family_mode=family, solo_test=solo, now=1000.0,
    )
    for index in range(players):
        room.add_player(f"J{index}", f"token-{index}", 1000.0, player_id=f"p{index}")
    assert room.start(1000.0)
    room.tick(1000.0)
    return room


def _snap(room, player_id="p0", now=1001.0):
    return room.snapshot("player", 0, now, player_id)["fiesta"]


def _act(room, player_id, kind, now=1001.0, **kwargs):
    return room.act(player_id, room.fiesta.round_index, kind, now, **kwargs)


def _vote_non_own(room, now=1020.0):
    for index in range(len(room.fiesta._players())):
        snap = _snap(room, f"p{index}", now)
        options = snap["options"]
        option = next((item for item in options if not item["isYours"]), None)
        if option is None and options:
            option = options[0]
        if option is not None:
            _act(room, f"p{index}", "fiesta_vote", now, target=option["id"])


def test_catalog_has_the_six_new_games():
    ids = {item["id"] for item in games.catalog()}
    assert set(fiesta.FIESTA_GAMES) <= ids
    for game in fiesta.FIESTA_GAMES:
        assert games.game_default_rounds(game) == fiesta.FIESTA_INFO[game]["rounds"]
        assert games.GAME_MIN_PLAYERS[game] == fiesta.FIESTA_INFO[game]["minPlayers"]


def test_ultima_palabra_duels_pair_players_and_bots():
    room = _room("ultima_palabra")
    assert room.phase == "write"
    prompts = []
    for index in range(3):
        snap = _snap(room, f"p{index}")
        prompts.append(snap["prompt"])
        assert _act(room, f"p{index}", "fiesta_submit", text=f"respuesta {index}")[0]
    assert room.phase == "duel"
    assert len(set(prompts)) == 2
    assert len(room.fiesta.duel_pairs) == 2
    # El jugador impar se enfrenta a NoraBot.
    assert any(pair["player_b"] == fiesta.BOT_PLAYER for pair in room.fiesta.duel_pairs)
    # Votan los que no son autores del duelo activo.
    for _ in range(6):
        if room.phase != "duel":
            break
        duel = _snap(room)["duelState"]
        pair = room.fiesta._active_duel()
        authors = {pair["player_a"], pair["player_b"]}
        voters = [p.id for p in room.fiesta._players() if p.id not in authors]
        if not voters:
            voters = [p.id for p in room.fiesta._players()]
        for pid in voters:
            _act(room, pid, "fiesta_vote", 1002.0, target="A")
    assert room.phase == "reveal"
    assert len(room.fiesta.duel_results) == 2
    assert room.fiesta.last_points.get("p0", 0) > 0
    assert "· NORA-KO" in json.dumps(room.fiesta.reveal, ensure_ascii=False)


def test_vox_populi_quick_chips_and_hilo_vote():
    room = _room("vox_populi")
    snap = _snap(room)
    chips = snap["you"]["quickChips"]
    assert len(chips) == 8
    assert len(set(chips)) == 8
    for index in range(3):
        assert _act(room, f"p{index}", "fiesta_submit", text=chips[index])[0]
    assert room.phase == "vote"
    assert _snap(room)["screen"] == "choice"
    hilo = _snap(room)["voxState"]
    assert hilo["rank3"]["rank"] == 3
    assert "rank" not in hilo["mysteryAnswer"]  # el ranking es secreto hasta el reveal
    mystery_rank = int(room.fiesta.vox_mystery["rank"])
    correct = "MAS_ARRIBA" if mystery_rank <= 2 else "MAS_ABAJO"
    for index in range(3):
        assert _act(room, f"p{index}", "fiesta_vote", target=correct)[0]
    assert room.phase == "reveal"
    assert room.fiesta.vox_guess_results
    assert room.fiesta.last_points


def test_vox_matching_ignores_accents_articles_and_typos():
    room = _room("vox_populi")
    assert room.fiesta._vox_match("la pizza", "pizza")
    assert room.fiesta._vox_match("piza", "pizza")
    assert not room.fiesta._vox_match("tacos", "pizza")


def test_fuera_de_epoca_dynamic_years_and_sorting():
    room = _room("fuera_de_epoca", rounds=2)
    snap = _snap(room)
    years = snap["years"]
    assert len(years) == 4
    assert len(set(years)) == 4
    correct = int(room.fiesta.data["correct"])
    correct_year = years[correct]
    assert 0 <= correct < 4
    assert all(1 <= year <= 2026 for year in years)
    assert all(year != correct_year or index == correct for index, year in enumerate(years))
    for index in range(3):
        assert _act(room, f"p{index}", "fiesta_submit", index=correct)[0]
    assert room.phase == "reveal"
    room.tick(1010.0)
    assert room.phase == "sort"
    sort_items = _snap(room)["sortItems"]
    assert len(sort_items) == 3
    assert all("year" not in item for item in sort_items)
    ordered = sorted(sort_items, key=lambda item: room.fiesta.sort_years[item["id"]])
    for index in range(3):
        assert _act(room, f"p{index}", "fiesta_sort", choices=[item["id"] for item in ordered])[0]
    assert room.phase == "reveal"
    assert any("Línea temporal" in line or "—" in line for line in room.fiesta.reveal["lines"])
    assert room.fiesta.last_points.get("p0", 0) >= 600


def test_texto_roto_three_subturns_goals_and_reactions():
    room = _room("texto_roto")
    now = 1001.0
    for subturn in range(3):
        for index in range(3):
            snap = _snap(room, f"p{index}", now)
            you = snap["you"]
            if you["chatRole"] == "WRITER":
                assert you["secretGoal"]
                assert _act(room, f"p{index}", "fiesta_submit", now + 0.1,
                            text=f"línea {index} subturno {subturn}")[0]
            else:
                messages = snap["chatMessages"]
                target = messages[-1]["id"] if messages else ""
                if target:
                    _act(room, f"p{index}", "fiesta_react", now + 0.2, target=target)
        now += 15.5
        room.tick(now)
    assert room.phase == "vote"
    assert len(room.fiesta.chat_messages) >= 3
    assert any(message["reactions"] > 0 for message in room.fiesta.chat_messages)
    _vote_non_own(room, now + 1)
    assert room.phase == "reveal"
    assert room.fiesta.reveal["title"] == "La conversación quedó así"


def test_franela_fest_remix_pool_and_credit_split():
    room = _room("franela_fest")
    strokes = [{"color": "#fff", "width": 8, "points": [[1, 1], [100, 100]]}]
    for index in range(3):
        assert _act(room, f"p{index}", "fiesta_draw", text=f"eslogan A {index}",
                    target=f"eslogan B {index}", strokes=strokes)[0]
    assert room.phase == "remix"
    for index in range(3):
        snap = _snap(room, f"p{index}")
        pool = snap["you"]["remixPool"]
        assert len(pool["drawings"]) == 2
        assert len(pool["slogans"]) == 4
        assert all(item["ownerId"] != f"p{index}" for item in pool["drawings"])
        assert all(item["ownerId"] != f"p{index}" for item in pool["slogans"])
        assert _act(room, f"p{index}", "fiesta_remix", 1002.0,
                    target=pool["drawings"][0]["id"], text=pool["slogans"][0]["id"])[0]
    assert room.phase == "vote"
    _vote_non_own(room, 1020.0)
    assert room.phase == "reveal"
    lines = " ".join(room.fiesta.reveal["lines"])
    assert "dibujo:" in lines
    assert "eslogan:" in lines


def test_el_tasador_sellers_buyers_and_hidden_lots():
    room = _room("el_tasador")
    roles = {pid: _snap(room, pid)["you"]["lotRole"] for pid in ("p0", "p1", "p2")}
    sellers = [pid for pid, role in roles.items() if role == "SELLER"]
    buyers = [pid for pid, role in roles.items() if role == "BUYER"]
    assert len(sellers) == 2 and len(buyers) == 1
    for pid in sellers:
        assert _act(room, pid, "fiesta_submit", text=f"pitch {pid}")[0]
    assert room.phase == "invest"
    pre = _snap(room)
    assert all(lot["realValue"] == 0 for lot in pre["lots"])
    assert all(lot["authentic"] is None for lot in pre["lots"])
    buyer = buyers[0]
    assert _act(room, buyer, "fiesta_invest", target="A", amount=100)[0]
    assert room.phase == "reveal"
    post = _snap(room)
    assert all(lot["realValue"] > 0 for lot in post["lots"])
    assert all(lot["authentic"] in (True, False) for lot in post["lots"])
    assert room.fiesta.lots["A"]["invested"] == 100


def test_tasador_two_players_use_bot_seller_and_one_buyer():
    room = _room("el_tasador", players=2)
    seller = next(p.id for p in room.fiesta._players() if _snap(room, p.id)["you"]["lotRole"] == "SELLER")
    buyer = next(p.id for p in room.fiesta._players() if p.id != seller)
    assert fiesta.BOT_SELLER in room.fiesta.sellers
    assert _act(room, seller, "fiesta_submit", text="pitch único")[0]
    assert room.phase == "invest"
    assert _act(room, buyer, "fiesta_invest", target="B", amount=250)[0]
    assert room.phase == "reveal"


def test_bet_only_pays_in_top_half():
    room = _room("fuera_de_epoca")
    correct = int(room.fiesta.data["correct"])
    for index in range(3):
        assert _act(room, f"p{index}", "fiesta_bet", amount=100)[0]
    for index in range(3):
        choice = correct if index < 2 else (correct + 1) % 4
        assert _act(room, f"p{index}", "fiesta_submit", index=choice)[0]
    assert room.phase == "reveal"
    assert room.fiesta.bet_results["p0"]["won"] is True
    assert room.fiesta.bet_results["p1"]["won"] is True
    assert room.fiesta.bet_results["p2"]["won"] is False
    assert room.fiesta.bet_results["p2"]["payout"] == 0


def test_taunt_targets_the_leader_and_creates_incoming_overlay():
    room = _room("ultima_palabra")
    assert _act(room, "p0", "fiesta_taunt", text="JA", target="p1")[0]
    victim = _snap(room, "p1")["you"]
    assert victim["incomingTaunt"]["from"] == "J0"
    assert victim["incomingTaunt"]["text"] == "JA"
    assert room.fiesta.taunts[-1]["targetPlayerId"] == "p1"


def test_bonus_only_twice_and_mimic_pays_last_place():
    room = _room("ultima_palabra", rounds=6)
    room.fiesta.round_index = 3
    assert room.fiesta._bonus_due() is True
    room.fiesta.round_index = 4
    assert room.fiesta._bonus_due() is False
    room.fiesta.round_index = 5
    assert room.fiesta._bonus_due() is True
    room.fiesta._bonus_done = True
    assert room.fiesta._bonus_due() is False
    # Mimic: fuerza el resultado y comprueba que no baja de 0 y paga al último.
    room.fiesta.phase = "bonus"
    room.fiesta.round_index = 5
    room.fiesta.mimic_hit = True
    room.fiesta.bonus_choices = {"p0": 0}
    room.fiesta.room.players["p0"].coins = 50
    before_last = room.fiesta.room.players["p2"].score
    room.fiesta._finish_bonus(1010.0)
    assert room.fiesta.room.players["p0"].coins >= 0
    assert room.fiesta.room.players["p2"].score >= before_last


def test_all_in_boost_costs_everything_and_applies_to_final_round():
    room = _room("ultima_palabra", rounds=6)
    room.fiesta.round_index = 5
    room.fiesta.phase = "reveal"
    room.fiesta.room.players["p0"].coins = 800
    assert _act(room, "p0", "fiesta_perk", target="all_in_boost")[0]
    assert room.fiesta.room.players["p0"].coins == 0
    assert room.fiesta.final_boost["p0"] == 1.5
    room.fiesta.round_index = 6
    before = room.fiesta.room.players["p0"].score
    room.fiesta._award("p0", 100)
    assert room.fiesta.room.players["p0"].score == before + 250


def test_audience_vote_marks_favorite_and_pays_bonus():
    room = _room("ultima_palabra")
    room.add_player("Espectador", "spec", 1000.0, player_id="s0", spectator=True)
    room.fiesta.phase = "vote"
    room.fiesta.options = [{"id": "A", "text": "a", "authorId": "p0", "votes": 0}]
    room.fiesta.audience_votes = {"s0": "A"}
    before = room.fiesta.last_points.get("p0", 0)
    room.fiesta._apply_audience_favorite(room.fiesta.options)
    assert room.fiesta.options[0]["audienceFavorite"] is True
    assert room.fiesta.last_points.get("p0", 0) > before


def test_every_active_phase_had_its_real_duration_not_the_8s_fallback():
    real = {
        "ultima_palabra": ("write", "duel"),
        "vox_populi": ("guess", "vote"),
        "fuera_de_epoca": ("guess", "sort"),
        "texto_roto": ("chat", "vote"),
        "franela_fest": ("create", "remix", "vote"),
        "el_tasador": ("pitch", "invest"),
    }
    for game, phases in real.items():
        for phase in phases:
            assert fiesta.DURATIONS[game].get(phase, 0) >= 15, (game, phase)


def test_every_used_host_moment_has_five_distinct_takes():
    moments = {
        "ultima_palabra": ("lobby", "start", "write", "vote", "reveal", "podium"),
        "vox_populi": ("lobby", "start", "guess", "reveal", "podium"),
        "fuera_de_epoca": ("lobby", "start", "guess", "reveal", "podium"),
        "texto_roto": ("lobby", "start", "write", "vote", "reveal", "podium"),
        "franela_fest": ("lobby", "start", "create", "vote", "reveal", "podium"),
        "el_tasador": ("lobby", "start", "pitch", "vote", "reveal", "podium"),
    }
    for game, labels in moments.items():
        for moment in labels:
            lines = fiesta_content.host_lines(game, moment)
            assert len(lines) >= 5, (game, moment, len(lines))
            assert len(set(lines)) == len(lines), (game, moment, "repetida")


def test_solo_test_mode_starts_and_advances_every_game():
    for game in fiesta.FIESTA_GAMES:
        room = _room(game, rounds=1, players=1, solo=True, code="SUN234")
        assert room.phase in ("write", "guess", "chat", "create", "pitch", "sort")
        if game == "ultima_palabra":
            _act(room, "p0", "fiesta_submit", text="solo")
            _act(room, "p0", "fiesta_vote", target="A")
        elif game == "vox_populi":
            _act(room, "p0", "fiesta_submit", text=_snap(room)["you"]["quickChips"][0])
            _act(room, "p0", "fiesta_vote", target="MAS_ARRIBA")
        elif game == "fuera_de_epoca":
            _act(room, "p0", "fiesta_submit", index=0)
        elif game == "texto_roto":
            for _ in range(3):
                _act(room, "p0", "fiesta_submit", text="solo")
                room.tick(room.fiesta.chat_subturn_started + 16.0)
            _act(room, "p0", "fiesta_vote", target="m0")
        elif game == "franela_fest":
            _act(room, "p0", "fiesta_draw", text="solo A", target="solo B",
                 strokes=[{"color": "#fff", "width": 8, "points": [[1, 1], [100, 100]]}])
            pool = _snap(room)["you"]["remixPool"]
            _act(room, "p0", "fiesta_remix", target=pool["drawings"][0]["id"], text=pool["slogans"][0]["id"])
            _vote_non_own(room)
        else:
            _act(room, "p0", "fiesta_submit", text="pitch solo")
            room.tick(1002.0)
        assert room.phase in ("reveal", "podium", "bonus"), game


def test_no_secret_leaks_before_reveal():
    # Duelo: las tarjetas públicas no llevan autor.
    room = _room("ultima_palabra")
    for index in range(3):
        _act(room, f"p{index}", "fiesta_submit", text=f"respuesta {index}")
    duel = _snap(room)["duelState"]
    assert "authorId" not in duel["optionA"]
    assert "authorName" not in duel["optionB"]
    # Vox: el ranking del misterio no viaja antes del reveal.
    vox = _room("vox_populi", code="VXN234")
    assert "rank" not in _snap(vox)["voxState"]["mysteryAnswer"]
    # Fuera: los items de sort no llevan año.
    fuera = _room("fuera_de_epoca", rounds=2, code="FUE234")
    fuera.tick(1010.0)
    assert all("year" not in item for item in _snap(fuera)["sortItems"])
    # Tasador: autenticidad y valor real ocultos antes del reveal.
    tas = _room("el_tasador", code="TAS234")
    assert all(lot["realValue"] == 0 and lot["authentic"] is None for lot in _snap(tas)["lots"])


def test_fase4_content_catalog_has_enough_variety():
    assert len(fiesta_content.VOX_QUESTIONS) >= 40
    assert all(len(item["answers"]) == 6 for item in fiesta_content.VOX_QUESTIONS)
    assert len(fiesta_content.TIME_EVENTS) >= 60
    assert len(fiesta_content.TEXTO_ROTO_SCENES) >= 40
    assert len(fiesta_content.FRANELA_PROMPTS) >= 40
    assert len(fiesta_content.TASADOR_OBJECTS) >= 40
    assert len(fiesta_content.SECRET_GOALS) >= 18
    assert len(set(fiesta_content.SECRET_GOALS)) == len(fiesta_content.SECRET_GOALS)
    assert len(fiesta_content.FRANELA_FALLBACK_SLOGANS) >= 20
    for game in fiesta.FIESTA_GAMES:
        assert len(fiesta.TAUNTS[game]) >= 9, game
        assert len(set(fiesta.TAUNTS[game])) == len(fiesta.TAUNTS[game]), game


def test_texto_roto_hides_authors_before_reveal_and_shows_them_after():
    room = _room("texto_roto", code="TXT234")
    writer = next(pid for pid, role in room.fiesta.chat_roles.items() if role == "WRITER")
    assert _act(room, writer, "fiesta_submit", text="mi línea secreta")[0]
    other = next(p.id for p in room.fiesta._players() if p.id != writer)
    messages = _snap(room, other)["chatMessages"]
    assert messages and messages[0]["authorName"] == "ANÓNIMO"
    assert _snap(room, writer)["chatMessages"][0]["authorName"] != "ANÓNIMO"
    room.fiesta._finish_texto_chat(1002.0)
    room.fiesta._finish_texto_vote(1003.0)
    revealed = _snap(room, other)
    assert revealed["chatMessages"][0]["authorName"] != "ANÓNIMO"


def test_network_latency_cushion_in_chat_and_duel():
    assert fiesta.DURATIONS["ultima_palabra"]["duel"] == 20
    assert fiesta.DURATIONS["texto_roto"]["chat"] == 18
    assert fiesta.CHAT_SUBTURN_SECONDS == 18.0
    # 3 subturnos de 18 s: la fase chat no puede cerrarse antes de 54 s.
    assert fiesta.CHAT_SUBTURN_SECONDS * 3 == 54.0
