"use client";
import {useEffect, useState} from 'react';

export default function QuestionVisual({src, alt}: {src: string; alt: string}) {
    const [error, setError] = useState(false);
    useEffect(() => setError(false), [src]);
    return <div className="question-visual">
        {error && <p role="alert">The question image could not be loaded. Refresh the exercise and try again.</p>}
        <img src={src} alt={alt} onError={() => setError(true)}/>
    </div>;
}
