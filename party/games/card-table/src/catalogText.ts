import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const cardTableText = {
  "de": {
    "displayName": "Baraja brava",
    "description": "Doce juegos de cartas, una mesa compartida y tu mano privada en el celular. Juega fino, roba o miente.",
    "visual": {
      "eyebrow": "Baraja"
    }
  },
  "en": {
    "displayName": "Baraja brava",
    "description": "Twelve card games, one shared table and your private hand on your phone. Play sharp, draw or lie.",
    "visual": {
      "eyebrow": "Baraja"
    },
    "lobbySetup": {
      "title": "Prepara la baraja",
      "description": "Choose a ruleset, deck and starting hand. Most rulesets support up to 6 players, Symbol Hunt up to 16 and Doppelkopf exactly 4.",
      "fields": {
        "ruleset": {
          "label": "Ruleset",
          "options": {
            "mau-mau": {
              "label": "Mau Mau",
              "description": "Sevens draw two, eights skip, nines reverse and jacks choose a suit."
            },
            "schwimmen": {
              "label": "Thirty-one",
              "description": "Three cards in hand and three face-up cards to swap. Knock to finish; each player has three lives."
            },
            "stichwette": {
              "label": "Trick Bets",
              "description": "Oferte exactamente cuántos trucos realizará. Una original baraja con coronas y plumas; Las manos crecen en cada ronda."
            },
            "zahlenreihe": {
              "label": "Number Rows",
              "description": "Construya cuatro filas del mismo palo del 1 al 20, cada una comenzando con once. Utiliza una baraja de números original."
            },
            "luegen": {
              "label": "Cheat",
              "description": "Play cards face down and claim the required rank. A failed challenge means taking the pile."
            },
            "schwarzer-peter": {
              "label": "Old Maid",
              "description": "Discard pairs and draw from your neighbour. The player left with the unpaired card loses."
            },
            "fischen": {
              "label": "Go Fish",
              "description": "Ask other players for a rank and collect four of a kind. Suitable for families."
            },
            "herzeln": {
              "label": "Hearts",
              "description": "Avoid tricks: each heart costs one point and the queen of spades costs thirteen."
            },
            "doppelkopf": {
              "label": "Doppelkopf",
              "description": "For four players: diamonds, queens, jacks and the ten of hearts are trumps. The club queens form the Re team."
            },
            "romme": {
              "label": "Rummy",
              "description": "Recoge conjuntos y carreras, haz una combinación inicial que valga 40 puntos y vacía tu mano usando comodines."
            },
            "symboljagd": {
              "label": "Symbol Hunt",
              "description": "Find the shared picture on your card and the centre card. Play solo against the clock or race other players. A wrong guess briefly locks you out."
            },
            "free-play": {
              "label": "Free Play",
              "description": "Una mesa abierta sin reglas obligatorias: se puede jugar cualquier carta."
            }
          }
        },
        "symboljagdSymbolsPerCard": {
          "label": "Symbols per card",
          "description": "More pictures make the match harder to spot. Every pair of cards still shares exactly one symbol.",
          "options": {
            "8": {
              "label": "8 symbols · Standard",
              "description": "57 pictures and 57 cards."
            },
            "9": {
              "label": "9 symbols · Challenging",
              "description": "73 pictures and 73 cards."
            },
            "10": {
              "label": "10 symbols · Expert",
              "description": "91 pictures and 91 cards."
            }
          }
        },
        "symboljagdDealMode": {
          "label": "Card target",
          "options": {
            "all": {
              "label": "All cards",
              "description": "Deal the complete deck."
            },
            "fixed": {
              "label": "Fixed cards per player",
              "description": "Everyone plays toward the same target. Used cards are reshuffled when needed."
            }
          }
        },
        "symboljagdCardTarget": {
          "label": "Cards per player",
          "description": "Target for the fixed-card mode; All cards uses the complete deck."
        },
        "deck": {
          "label": "Card deck",
          "description": "Deck used by this ruleset.",
          "options": {
            "french-52": {
              "label": "French (52)",
              "description": "Spades, hearts, diamonds and clubs, from 2 to ace."
            },
            "french-54": {
              "label": "French + jokers (54)",
              "description": "The same deck plus two jokers."
            },
            "skat-32": {
              "label": "German (32)",
              "description": "Acorns, leaves, hearts and bells, from 7 to ace."
            },
            "party-40": {
              "label": "Party deck (40)",
              "description": "Una baraja de muestra original con sus propios símbolos."
            },
            "french-104": {
              "label": "Double deck (108)",
              "description": "Two French decks and four jokers."
            }
          }
        },
        "handSize": {
          "label": "Starting hand",
          "description": "Cards dealt to each player."
        },
        "dimUnplayable": {
          "label": "Dim unplayable cards",
          "description": "Highlight playable cards on the phone. Turn off to show every card normally; the rules still apply."
        },
        "handSort": {
          "label": "Sort your hand",
          "description": "Group cards on the phone by trumps and suits instead of the order in which they were dealt."
        },
        "botCount": {
          "label": "AI players",
          "description": "Virtual players controlled by the table follow the same rules and fill up to six seats. Missing players are added automatically when a ruleset needs a minimum number."
        },
        "cardStyle": {
          "label": "Card artwork",
          "options": {
            "classic": {
              "label": "Classic",
              "description": "White cards with serif text, corner marks and suit pips."
            },
            "modern": {
              "label": "Modern",
              "description": "Coloured panels and large numbers, readable at a distance."
            },
            "clear": {
              "label": "Clear",
              "description": "Minimal artwork with a huge number and a small symbol."
            }
          }
        },
        "mauStackSevens": {
          "label": "Stack sevens",
          "description": "A seven passes the accumulated draw penalty to the next player.",
          "group": "Mau Mau house rules"
        },
        "mauSkipEights": {
          "label": "Eights skip",
          "description": "Jugar un ocho salta al siguiente jugador.",
          "group": "Mau Mau house rules"
        },
        "mauReverseNines": {
          "label": "Nines reverse",
          "description": "A nine reverses play. With two players, it skips the other player.",
          "group": "Mau Mau house rules"
        },
        "mauJackOnJack": {
          "label": "Jack on jack",
          "description": "Jacks and jokers may also be played on other jacks or jokers.",
          "group": "Mau Mau house rules"
        },
        "mauPlayDrawn": {
          "label": "Play a drawn card immediately",
          "description": "After a normal draw, you may immediately play the drawn card. Penalty draws have a separate option.",
          "group": "Mau Mau house rules"
        },
        "mauPlayPenaltyDrawn": {
          "label": "Play after a penalty draw",
          "description": "After drawing a penalty, you may play a matching card from those just drawn. Previously held cards remain unavailable.",
          "group": "Mau Mau house rules"
        },
        "dokoNines": {
          "label": "Without nines (40 cards)",
          "description": "The king of diamonds becomes the lowest trump and rounds are shorter.",
          "group": "Doppelkopf house rules"
        },
        "dokoSecondDulle": {
          "label": "Second ten of hearts wins",
          "description": "If both tens of hearts enter one trick, the second one wins.",
          "group": "Doppelkopf house rules"
        },
        "dokoDoppelkopf": {
          "label": "Doppelkopf (40+ card points)",
          "description": "Award a bonus point for a trick worth at least 40 card points.",
          "group": "Doppelkopf house rules"
        },
        "dokoFox": {
          "label": "Catch the fox",
          "description": "Otorga un punto de bonificación por capturar el as de diamantes de un oponente.",
          "group": "Doppelkopf house rules"
        },
        "dokoCharlie": {
          "label": "Charlie in the last trick",
          "description": "Award a bonus point for the jack of clubs in the final trick.",
          "group": "Doppelkopf house rules"
        },
        "dokoFoxEnd": {
          "label": "Fox in the last trick",
          "description": "Otorgue un punto de bonificación por un as de diamantes en la baza final.",
          "group": "Doppelkopf house rules"
        },
        "dokoPigs": {
          "label": "Super-trump aces",
          "description": "Holding both aces of diamonds makes them the highest trumps.",
          "group": "Doppelkopf house rules"
        },
        "dokoAgainstOld": {
          "label": "Against the queens",
          "description": "Award a bonus point when the Kontra team wins.",
          "group": "Doppelkopf house rules"
        },
        "dokoBock": {
          "label": "Double-score round",
          "description": "After a failed Re call or a team taking no tricks, the next round scores double.",
          "group": "Doppelkopf house rules"
        },
        "dokoForced": {
          "label": "Mandatory call at 35 card points",
          "description": "The winner of the first trick reveals their team.",
          "group": "Doppelkopf house rules"
        },
        "doppelkopfScoring": {
          "label": "Count card points only at the end",
          "description": "Hide the running totals; score the round after the last trick.",
          "group": "Doppelkopf house rules"
        }
      }
    }
  }
} satisfies Partial<LocalizedGameTextMap>;
