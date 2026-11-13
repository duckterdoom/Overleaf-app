"use client";



export function CommutingChart() {
    return (
        <svg
            viewBox="0 0 420 245"
            role="img"
            aria-label="Practice chart: car, bus and bicycle commuting in 2000 and 2020"
            className="writing-chart"
        >
            <text
                x="210"
                y="22"
                textAnchor="middle"
                fontSize="14"
                fill="currentColor"
            >
                Commuting methods in a city (%)
            </text>
            {[0, 20, 40, 60].map((n) => (
                <g key={n}>
                    <path d={`M40 ${200 - n * 2.3}h350`} stroke="#cbd5cf" />
                    <text
                        x="30"
                        y={204 - n * 2.3}
                        textAnchor="end"
                        fontSize="11"
                        fill="currentColor"
                    >
                        {n}
                    </text>
                </g>
            ))}
            {[
                [60, 45],
                [25, 30],
                [15, 25],
            ].map((pair, i) => (
                <g key={i}>
                    <rect
                        x={70 + i * 110}
                        y={200 - pair[0] * 2.3}
                        width="25"
                        height={pair[0] * 2.3}
                        fill="#216e4b"
                    />
                    <rect
                        x={100 + i * 110}
                        y={200 - pair[1] * 2.3}
                        width="25"
                        height={pair[1] * 2.3}
                        fill="#9cbda8"
                    />
                    <text
                        x={98 + i * 110}
                        y="224"
                        fontSize="12"
                        textAnchor="middle"
                        fill="currentColor"
                    >
                        {["Car", "Bus", "Bicycle"][i]}
                    </text>
                </g>
            ))}
            <text x="260" y="47" fill="#216e4b" fontSize="11">
                ■ 2000
            </text>
            <text x="330" y="47" fill="#769d82" fontSize="11">
                ■ 2020
            </text>
        </svg>
    );
}

export {default} from './ExerciseForm';
