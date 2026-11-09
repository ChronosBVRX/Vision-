const STYLE_ID = "schaetzorama-dom-host-styles";

const css = `
.sz-host-mount { z-index: 1; }
.sz-host, .sz-host * { box-sizing: border-box; }
.sz-host {
  --sz-number: #3f806a; --sz-percent: #39749b; --sz-rank: #9a527b; --sz-assign: #b86c32;
  position: absolute; inset: 0; overflow: hidden; background: linear-gradient(180deg, rgba(247,241,231,.90), rgba(247,241,231,.96)), url('/schaetzorama/ambient.webp') center/cover no-repeat, var(--paper); color: var(--ink); font-family: var(--font-body);
}
.sz-host h1, .sz-host h2, .sz-host p { margin: 0; }
.sz-host-header {
  position: absolute; inset: 0 0 auto; height: 94px; display: grid; grid-template-columns: 1fr auto 1fr;
  align-items: center; gap: 24px; padding: 18px 32px; border-bottom: 1px solid var(--line);
  background: color-mix(in srgb, var(--surface) 94%, transparent); z-index: 3;
}
.sz-brand, .sz-round, .sz-live-meta { display: grid; gap: 3px; }
.sz-brand strong { font: 700 25px/1 var(--font-display); }
.sz-brand span, .sz-round span, .sz-live-meta b { color: var(--muted); font-size: 13px; font-weight: 650; }
.sz-round { text-align: center; }.sz-round strong { font-size: 17px; }.sz-live-meta { justify-items: end; }
.sz-live-meta span { padding: 7px 12px; border: 1px solid var(--line-strong); border-radius: 999px; font-weight: 800; }

.sz-intro { display: grid; place-items: center; isolation: isolate; }
.sz-intro::before { content: ""; position: absolute; inset: 0; opacity: .34; background-image: linear-gradient(var(--line) 1px, transparent 1px), linear-gradient(90deg, var(--line) 1px, transparent 1px); background-size: 44px 44px; mask-image: linear-gradient(to bottom, transparent, black 25%, black 75%, transparent); }
.sz-intro__bands { position: absolute; inset: 8% 0; display: grid; align-content: center; gap: 12px; transform: rotate(-4deg) scale(1.08); }
.sz-intro__bands span { display: block; height: 42px; transform-origin: left; animation: sz-band-in 700ms cubic-bezier(.2,.8,.2,1) both; animation-delay: var(--delay); opacity: .18; }
.sz-intro__bands .is-number { background: var(--sz-number); width: 82%; }.sz-intro__bands .is-percent { background: var(--sz-percent); width: 64%; margin-left: auto; }
.sz-intro__bands .is-rank { background: var(--sz-rank); width: 74%; }.sz-intro__bands .is-assign { background: var(--sz-assign); width: 88%; margin-left: auto; }
.sz-intro__content { position: relative; z-index: 1; width: min(820px, 78%); text-align: center; }
.sz-kicker { color: var(--accent-strong); font-size: 15px; font-weight: 900; text-transform: uppercase; }
.sz-intro h1 { margin-top: 12px; font: 700 78px/.95 var(--font-display); }
.sz-intro__content > p:not(.sz-kicker) { margin-top: 20px; color: var(--ink-soft); font-size: 23px; }
.sz-intro__legend { display: flex; justify-content: center; gap: 10px; margin-top: 34px; }
.sz-intro__legend span { display: inline-flex; align-items: center; gap: 8px; padding: 9px 13px; background: var(--surface); border: 1px solid var(--line); border-radius: 8px; font-weight: 800; box-shadow: var(--shadow-card); }
.sz-intro__legend .is-number { color: var(--sz-number); }.sz-intro__legend .is-percent { color: var(--sz-percent); }.sz-intro__legend .is-rank { color: var(--sz-rank); }.sz-intro__legend .is-assign { color: var(--sz-assign); }
.sz-room-code { position: absolute; right: 30px; bottom: 24px; color: var(--muted); }.sz-room-code strong { color: var(--ink); letter-spacing: .12em; }

.sz-play { display: grid; grid-template-columns: minmax(0, 1fr) 300px; grid-template-rows: 94px minmax(0, 1fr); }
.sz-play .sz-host-header { grid-column: 1 / -1; }
.sz-question-grid { grid-column: 1; grid-row: 2; min-height: 0; display: grid; grid-template-columns: 1fr 1fr; grid-template-rows: 1fr 1fr; gap: 14px; padding: 18px; }
.sz-question { --cat: var(--sz-number); position: relative; min-width: 0; display: grid; align-content: space-between; gap: 12px; padding: 20px; overflow: hidden; border: 1px solid var(--line); border-top: 5px solid var(--cat); border-radius: 8px; background: var(--surface); box-shadow: var(--shadow-card); animation: sz-card-in 480ms ease-out both; animation-delay: var(--enter-delay); }
.sz-question.is-percent { --cat: var(--sz-percent); }.sz-question.is-rank { --cat: var(--sz-rank); }.sz-question.is-assign { --cat: var(--sz-assign); }
.sz-question__top { display: grid; grid-template-columns: 44px minmax(0,1fr) 70px; align-items: center; gap: 12px; }
.sz-category-mark { width: 42px; height: 42px; display: grid; place-items: center; border-radius: 50%; background: color-mix(in srgb, var(--cat) 16%, var(--surface)); color: var(--cat); font-size: 19px; font-weight: 950; }
.sz-question__top div:nth-child(2) { display: grid; gap: 2px; }.sz-question__top small { color: var(--cat); font-size: 12px; font-weight: 900; text-transform: uppercase; }.sz-question__top strong { color: var(--muted); font-size: 13px; }
.sz-question h2 { max-width: 93%; font: 650 23px/1.24 var(--font-display); letter-spacing: 0; }.sz-question > p { color: var(--muted); font-size: 14px; font-weight: 750; text-transform: uppercase; }
.sz-mini-scale { position: relative; display: flex; justify-content: space-between; align-items: end; height: 34px; border-bottom: 2px solid var(--cat); }.sz-mini-scale i { width: 2px; height: 8px; background: var(--cat); }.sz-mini-scale b { position: absolute; left: 54%; bottom: -6px; width: 12px; height: 12px; border-radius: 50%; background: var(--cat); }
.sz-mini-donut { width: 42px; aspect-ratio: 1; margin-left: auto; border-radius: 50%; background: conic-gradient(var(--cat) 0 68%, color-mix(in srgb, var(--cat) 15%, transparent) 68%); mask: radial-gradient(circle, transparent 43%, black 45%); }
.sz-mini-rank { display: grid; gap: 5px; justify-items: end; }.sz-mini-rank i { height: 5px; background: var(--cat); border-radius: 2px; }.sz-mini-rank i:nth-child(1){width:65px}.sz-mini-rank i:nth-child(2){width:49px}.sz-mini-rank i:nth-child(3){width:34px}
.sz-mini-venn { position: relative; width: 60px; height: 38px; }.sz-mini-venn i { position:absolute; width:36px; height:36px; border:3px solid var(--cat); border-radius:50%; }.sz-mini-venn i:last-child{left:22px;opacity:.62}
.sz-score-rail { grid-column: 2; grid-row: 2; min-height: 0; display: grid; grid-template-rows: auto auto minmax(0,1fr); gap: 14px; padding: 18px 18px 18px 4px; border-left: 1px solid var(--line); }
.sz-score-rail__head { display:flex; justify-content:space-between; align-items:center; }.sz-score-rail__head span{color:var(--muted);font-size:13px;font-weight:800;text-transform:uppercase}.sz-score-rail__head strong{font-size:20px}
.sz-progress-list { display:grid; grid-template-columns:1fr 1fr; gap:7px; }.sz-player-progress { min-width:0; display:grid; grid-template-columns:8px minmax(0,1fr) auto; align-items:center; gap:7px; padding:8px; border:1px solid var(--line); border-radius:6px; background:var(--surface-muted); opacity:.56; }.sz-player-progress.is-done{opacity:1;background:var(--surface)}.sz-player-progress i{width:8px;height:8px;border-radius:50%;background:var(--player)}.sz-player-progress span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px;font-weight:750}.sz-player-progress b{color:var(--sage-strong)}
.sz-standings { min-height:0; overflow:hidden; padding-top:12px; border-top:1px solid var(--line); }.sz-standings>p{margin-bottom:8px;color:var(--muted);font-size:12px;font-weight:900;text-transform:uppercase}.sz-standings>div{display:flex;justify-content:space-between;gap:10px;padding:8px 3px;border-bottom:1px solid var(--line)}.sz-standings span{display:flex;gap:8px;min-width:0;font-size:13px}.sz-standings span b{color:var(--muted);font-weight:650}.sz-standings>div>strong{font-variant-numeric:tabular-nums}

.sz-reveal { --cat: var(--sz-number); padding-top:94px; }.sz-reveal.is-percent{--cat:var(--sz-percent)}.sz-reveal.is-rank{--cat:var(--sz-rank)}.sz-reveal.is-assign{--cat:var(--sz-assign)}
.sz-reveal__main { height:calc(100% - 38px); display:grid; grid-template-columns:minmax(0, .92fr) minmax(480px, 1.08fr); gap:24px; padding:24px 32px 18px; }
.sz-reveal__question { align-self:center; padding-right:26px; }.sz-reveal__eyebrow{display:flex;align-items:flex-start;gap:13px;color:var(--ink-soft);font-size:19px;line-height:1.35}.sz-reveal__eyebrow span{flex:0 0 44px;width:44px;height:44px;display:grid;place-items:center;border-radius:50%;background:var(--cat);color:white;font-weight:950}.sz-reveal__question>p{margin-top:46px;color:var(--cat);font-size:13px;font-weight:950;text-transform:uppercase}.sz-reveal__question h1{margin-top:8px;font:700 50px/1.06 var(--font-display);letter-spacing:0;animation:sz-solution-in 520ms cubic-bezier(.16,.8,.28,1) both}.sz-reveal__question small{display:block;margin-top:22px;color:var(--muted)}
.sz-answer-board { align-self:center; display:grid; gap:8px; }.sz-answer-board>p{margin-bottom:3px;color:var(--muted);font-size:12px;font-weight:900;text-transform:uppercase}.sz-answer-row{display:grid;grid-template-columns:28px minmax(100px,.7fr) minmax(170px,1.3fr) auto 56px;align-items:center;gap:10px;padding:12px 14px;border:1px solid var(--line);border-left:6px solid var(--player);border-radius:7px;background:var(--surface);box-shadow:var(--shadow-card);animation:sz-row-in 420ms ease-out both;animation-delay:var(--row-delay)}.sz-answer-row__rank{color:var(--muted);font-weight:850}.sz-answer-row>strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.sz-answer-row>span:nth-child(3){color:var(--ink-soft);font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.sz-answer-row em{padding:3px 6px;border-radius:4px;background:var(--amber-soft);color:var(--ink);font-size:10px;font-style:normal;font-weight:900;text-transform:uppercase}.sz-answer-row>b{text-align:right;color:var(--cat);font-size:23px}
.sz-reveal-steps{position:absolute;inset:auto 32px 16px;display:grid;grid-template-columns:repeat(4,1fr);gap:8px}.sz-reveal-steps i{height:6px;border-radius:3px;background:var(--line-strong);opacity:.45}.sz-reveal-steps i.is-active{opacity:1}.sz-reveal-steps .is-number{background:var(--sz-number)}.sz-reveal-steps .is-percent{background:var(--sz-percent)}.sz-reveal-steps .is-rank{background:var(--sz-rank)}.sz-reveal-steps .is-assign{background:var(--sz-assign)}

.sz-final { padding-top:94px; }.sz-final__title{padding:22px 32px 10px}.sz-final__title p{color:var(--muted);font-size:16px}.sz-final-board{display:grid;gap:9px;padding:10px 32px 26px}.sz-final-row{display:grid;grid-template-columns:42px 10px minmax(120px,.7fr) minmax(340px,1.5fr) 120px;align-items:center;gap:15px;min-height:58px;padding:10px 17px;border:1px solid var(--line);border-radius:7px;background:var(--surface);animation:sz-row-in 420ms ease-out both;animation-delay:var(--row-delay)}.sz-final-row.is-winner{min-height:72px;border-color:color-mix(in srgb,var(--player) 55%,var(--line));box-shadow:var(--shadow-panel)}.sz-final-row__place{font:700 24px var(--font-display);color:var(--muted)}.sz-final-row>i{width:10px;height:34px;border-radius:3px;background:var(--player)}.sz-final-row>strong{font-size:18px}.sz-final-row__breakdown{display:flex;gap:6px;flex-wrap:wrap}.sz-final-row__breakdown span{padding:5px 7px;border-radius:5px;background:var(--surface-muted);font-size:12px;font-weight:800}.sz-final-row__breakdown .is-number{color:var(--sz-number)}.sz-final-row__breakdown .is-percent{color:var(--sz-percent)}.sz-final-row__breakdown .is-rank{color:var(--sz-rank)}.sz-final-row__breakdown .is-assign{color:var(--sz-assign)}.sz-final-row>b{text-align:right;font-size:26px}.sz-final-row>b small{display:block;color:var(--muted);font-size:10px;text-transform:uppercase}

@keyframes sz-band-in{from{transform:scaleX(0)}to{transform:scaleX(1)}}
@keyframes sz-card-in{from{opacity:0;transform:translateY(14px)}to{opacity:1;transform:none}}
@keyframes sz-solution-in{from{opacity:0;transform:translateY(18px) scale(.97)}to{opacity:1;transform:none}}
@keyframes sz-row-in{from{opacity:0;transform:translateX(26px)}to{opacity:1;transform:none}}
@media (max-width:1040px){.sz-host-header{grid-template-columns:1fr auto}.sz-round{display:none}.sz-play{grid-template-columns:minmax(0,1fr) 260px}.sz-question h2{font-size:20px}.sz-reveal__main{grid-template-columns:.8fr 1.2fr;padding-inline:22px}.sz-reveal__question h1{font-size:40px}.sz-final-row{grid-template-columns:38px 8px minmax(100px,.6fr) 1.4fr 100px}}
@media (max-height:680px){.sz-host-header{height:78px;padding-block:12px}.sz-play{grid-template-rows:78px minmax(0,1fr)}.sz-reveal,.sz-final{padding-top:78px}.sz-question-grid{padding-block:12px}.sz-question{padding:15px}.sz-question h2{font-size:19px}.sz-final__title{padding-block:12px 6px}.sz-final-board{gap:6px;padding-top:5px}.sz-final-row{min-height:48px;padding-block:7px}.sz-final-row.is-winner{min-height:58px}}
.sz-meta-actions{display:flex;align-items:center;gap:9px}.sz-meta-actions button{width:38px;height:38px;padding:0;border:1px solid var(--line-strong);border-radius:7px;background:var(--surface);color:var(--ink);font-size:21px;cursor:pointer}.sz-meta-actions button[aria-pressed=false]{color:var(--muted)}
.sz-reveal.is-assign .sz-reveal__main,.sz-reveal.is-rank .sz-reveal__main{grid-template-columns:minmax(280px,.7fr) minmax(0,1.3fr);gap:20px}
.sz-reveal.is-assign .sz-reveal__question,.sz-reveal.is-rank .sz-reveal__question{padding-right:0;min-width:0}
.sz-reveal.is-assign .sz-reveal__question>p,.sz-reveal.is-rank .sz-reveal__question>p{margin-top:20px;margin-bottom:12px}
.sz-order-solution>p{color:var(--muted);font-size:14px;margin-bottom:10px}.sz-order-list{display:grid;gap:8px}
.sz-order-item{height:52px;position:relative;display:grid;grid-template-columns:32px minmax(0,1fr) 24px;align-items:center;gap:10px;padding:7px 12px;border:1px solid var(--line-strong);border-radius:7px;background:var(--surface);animation:sz-sort-in 1400ms cubic-bezier(.2,.75,.2,1) both;animation-delay:calc(var(--item-delay) - var(--reveal-age,0ms))}
.sz-order-item>b{color:var(--cat);font-size:22px}.sz-order-item>strong{font-size:16px;overflow-wrap:anywhere}.sz-order-item>span{color:var(--cat);animation:sz-check-in 250ms both;animation-delay:calc(var(--item-delay) + 1250ms - var(--reveal-age,0ms))}
.sz-zone-labels{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:5px;text-align:center;margin-bottom:8px}.sz-zone-labels>strong{padding:8px 4px;border-bottom:3px solid var(--cat);font-size:15px;overflow-wrap:anywhere}
.sz-zone-rail{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));min-height:54px;margin-bottom:7px;background:linear-gradient(90deg,transparent 33%,var(--line) 33%,var(--line) 33.4%,transparent 33.4%,transparent 66.3%,var(--line) 66.3%,var(--line) 66.7%,transparent 66.7%);border-bottom:1px dashed var(--line)}
.sz-zone-item{grid-column:1;display:flex;gap:6px;align-items:center;justify-content:center;min-width:0;min-height:54px;padding:7px;border:2px solid var(--cat);border-radius:7px;background:var(--surface);text-align:center;font-size:15px;overflow-wrap:anywhere;animation:sz-zone-slide 1400ms cubic-bezier(.2,.75,.2,1) both;animation-delay:calc(var(--item-delay) - var(--reveal-age,0ms))}.sz-zone-item>span{color:var(--cat);animation:sz-check-in 250ms both;animation-delay:calc(var(--item-delay) + 1250ms - var(--reveal-age,0ms))}
.sz-reveal .sz-answer-row{grid-template-columns:24px minmax(70px,.8fr) minmax(80px,1fr) auto 46px;gap:7px;padding:10px;animation-delay:calc(var(--row-delay) - var(--reveal-age,0ms))}
.sz-reveal .sz-answer-row>b{animation:sz-points-pop 480ms both;animation-delay:calc(var(--row-delay) + 180ms - var(--reveal-age,0ms))}.sz-answer-row em[hidden]{display:none}
.sz-reveal__question h1{animation-delay:calc(650ms - var(--reveal-age,0ms))}
.sz-numeric-answer{display:grid;gap:3px;overflow-wrap:anywhere}.sz-numeric-answer strong{font-size:18px}.sz-numeric-answer small{color:var(--ink-soft);font-size:12px;line-height:1.25}
.sz-comparison-wrap{min-width:0;max-height:100%;overflow:auto;align-self:center}.sz-comparison{width:100%;border-collapse:collapse;table-layout:fixed;font-size:13px;line-height:1.3}.sz-comparison caption{text-align:left;color:var(--ink-soft);font-weight:800;margin-bottom:12px;font-size:16px}
.sz-comparison th,.sz-comparison td{padding:10px 6px;border-bottom:1px solid var(--line);overflow-wrap:anywhere;text-align:center;vertical-align:middle}.sz-comparison th:first-child{width:110px;text-align:left}.sz-comparison th:last-child{width:58px}.sz-comparison thead th{font-size:12px;color:var(--ink-soft)}.sz-comparison thead{background:var(--paper)}
.sz-comparison-solution{background:color-mix(in srgb,var(--cat) 10%,var(--surface));font-weight:800}.sz-comparison tbody tr{animation:sz-row-in 420ms both;animation-delay:calc(var(--row-delay) - var(--reveal-age,0ms))}.sz-comparison tbody th small{display:block;font-size:10px;color:var(--muted)}
.sz-comparison .is-correct{background:color-mix(in srgb,#188255 10%,var(--surface));color:var(--ink)}.sz-comparison .is-wrong{background:color-mix(in srgb,#c34c57 12%,var(--surface));color:var(--ink)}.sz-comparison td>span{font-size:17px;font-weight:900}.sz-comparison .is-correct>span{color:var(--sz-number)}.sz-comparison .is-wrong>span{color:var(--ink)}.sz-comparison-points{font-size:18px;font-weight:900;color:var(--cat)}
@media(max-width:1050px){.sz-reveal.is-assign .sz-reveal__main,.sz-reveal.is-rank .sz-reveal__main{grid-template-columns:minmax(230px,.55fr) minmax(0,1.45fr);gap:14px;padding-inline:16px}.sz-comparison th:first-child{width:82px}.sz-comparison th,.sz-comparison td{padding:8px 4px}}
@media(max-width:700px){.sz-reveal{overflow:auto}.sz-reveal .sz-reveal__main{height:auto;display:flex;flex-direction:column;padding-bottom:48px}.sz-comparison-wrap{width:100%;max-height:none}.sz-comparison{min-width:560px}.sz-reveal-steps{position:fixed}}
@keyframes sz-sort-in{from{transform:translateY(calc(var(--from-row) * 60px));border-color:var(--line)}to{transform:translateY(0);border-color:var(--cat)}}
@keyframes sz-zone-slide{from{transform:translateX(100%)}to{transform:translateX(calc(var(--zone) * 100%))}}
@keyframes sz-check-in{from{opacity:0;transform:scale(.5)}to{opacity:1;transform:scale(1)}}
@keyframes sz-points-pop{0%{opacity:0;transform:translateY(10px) scale(.8)}65%{opacity:1;transform:translateY(-3px) scale(1.12)}100%{opacity:1;transform:none}}
@media(max-height:680px){.sz-zone-rail,.sz-zone-item{min-height:42px}.sz-zone-item{font-size:13px;padding:4px}.sz-reveal__eyebrow{font-size:16px}.sz-reveal__question small{margin-top:12px}}
.sz-movement-board{display:grid;gap:8px;padding:10px 32px 24px}
.sz-movement-row{height:56px;box-sizing:border-box;display:grid;grid-template-columns:42px 8px minmax(0,1fr) 60px 88px 100px;align-items:center;gap:14px;padding:8px 16px;border:1px solid var(--line);border-radius:7px;background:var(--surface);animation:sz-standing-move 1200ms cubic-bezier(.2,.8,.2,1) both;animation-delay:calc(1200ms - var(--reveal-age,0ms))}
.sz-movement-row>i{height:30px;background:var(--player);border-radius:3px}.sz-movement-row>strong{overflow-wrap:anywhere;font-size:19px}.sz-movement-place,.sz-movement-total{display:grid;font-size:24px}.sz-movement-place>b,.sz-movement-total>b{grid-area:1/1}.sz-movement-total{text-align:right}.sz-movement-gain{font-size:20px;color:var(--sz-number);text-align:right}.sz-movement-shift{font-size:18px;text-align:center}
.sz-before{animation:sz-old-standing 1ms both;animation-delay:calc(1200ms - var(--reveal-age,0ms))}.sz-after{animation:sz-new-standing 1ms both;animation-delay:calc(1200ms - var(--reveal-age,0ms))}
@keyframes sz-standing-move{from{transform:translateY(calc(var(--from-row) * 64px))}to{transform:translateY(0)}}
@keyframes sz-old-standing{from{opacity:1}to{opacity:0}}@keyframes sz-new-standing{from{opacity:0}to{opacity:1}}
.sz-reveal-ready{position:absolute;bottom:32px;left:32px;right:32px;text-align:right;color:var(--muted);font-size:16px;font-weight:750}
.sz-reveal__main{height:calc(100% - 70px)}
.sz-answer-board{min-height:0;max-height:100%;overflow-y:auto;overflow-x:hidden;gap:10px;padding:4px}
.sz-reveal .sz-answer-row{grid-template-columns:24px minmax(90px,.85fr) minmax(110px,1.2fr) auto 60px;gap:12px;padding:14px 16px;border-radius:12px;animation-duration:650ms}
.sz-answer-row>strong{font-size:clamp(18px,1.5vw,25px)}
.sz-numeric-answer strong{font-size:clamp(26px,2.7vw,40px);line-height:1.1;font-variant-numeric:tabular-nums;letter-spacing:-.03em}
.sz-numeric-answer small{font-size:14px}.sz-reveal .sz-answer-row>b{font-size:28px}
.sz-comparison{font-size:16px}.sz-comparison tbody th{font-size:18px}.sz-comparison-points{font-size:24px}
.sz-comparison tbody tr{animation-duration:650ms}
.sz-gauge{position:relative;width:min(100%,380px);height:210px;margin:28px auto 0}
.sz-gauge-arc{position:absolute;inset:0 0 30px;border:18px solid var(--line-strong);border-bottom:0;border-radius:380px 380px 0 0;background:linear-gradient(0deg,transparent,color-mix(in srgb,var(--cat) 7%,transparent))}
.sz-gauge-needle{position:absolute;left:calc(50% - 3px);bottom:30px;width:6px;height:75%;border-radius:6px;background:var(--cat);transform-origin:50% 100%;animation:sz-needle-sweep 1400ms cubic-bezier(.4,0,.2,1) both;animation-delay:calc(650ms - var(--reveal-age,0ms))}
.sz-gauge-hub{position:absolute;bottom:20px;left:calc(50% - 12px);width:24px;height:24px;border:5px solid var(--surface);border-radius:50%;background:var(--cat);box-shadow:0 0 0 5px color-mix(in srgb,var(--cat) 18%,transparent)}
.sz-gauge-min,.sz-gauge-max{position:absolute;bottom:0;font-size:18px;font-weight:800;color:var(--ink-soft)}.sz-gauge-min{left:0}.sz-gauge-max{right:0}
.sz-reveal__question h1{font-size:clamp(42px,4.4vw,68px);text-align:center;animation-delay:calc(2050ms - var(--reveal-age,0ms))}
@keyframes sz-needle-sweep{from{transform:rotate(-90deg)}to{transform:rotate(var(--needle-angle))}}
.sz-is-paused .sz-host *{animation-play-state:paused!important}
/* Keep every player reachable in large groups, including short host viewports. */
.sz-progress-list{min-height:0;max-height:35vh;overflow:auto}
.sz-standings{min-height:0;overflow:auto}
.sz-standings>div>span{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.sz-final{display:flex;flex-direction:column}
.sz-final__title{flex-shrink:0}
.sz-final-board,.sz-movement-board{min-height:0;overflow:auto;flex:1;align-content:start}
.sz-final-row>strong{min-width:0;overflow-wrap:anywhere}
@media(max-height:740px){.sz-gauge{height:155px;width:280px;margin-top:16px}.sz-reveal .sz-answer-row{padding:9px 12px}.sz-reveal__question>p{margin-top:22px}.sz-reveal__question h1{font-size:44px}}
@media(max-width:1040px){.sz-reveal .sz-answer-row{grid-template-columns:20px minmax(65px,.7fr) minmax(90px,1fr) auto 48px;gap:7px}.sz-numeric-answer strong{font-size:26px}.sz-answer-row>strong{font-size:18px}}
@media (prefers-reduced-motion:reduce){.sz-host *{animation-duration:.01ms!important;animation-delay:0ms!important}}
`;

export function installSchaetzoramaHostStyles(): void {
  if (document.getElementById(STYLE_ID)) return;
  const style = document.createElement("style");
  style.id = STYLE_ID;
  style.textContent = css;
  document.head.appendChild(style);
}
