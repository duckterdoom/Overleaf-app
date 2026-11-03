import { translator, type Activity } from "../lib/parity";
export default function ActivityHeatmap({
    activity,
    language,
}: {
    activity: Activity;
    language: string;
}) {
    const t = translator(language),
        today = new Date();
    const dayKey = (day: Date) =>
        `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`;
    let streak = 0;
    const cursor = new Date(today);
    while ((activity.edits[dayKey(cursor)] || 0) > 0) {
        streak++;
        cursor.setDate(cursor.getDate() - 1);
    }
    const activeDays = Object.entries(activity.edits).filter(
        ([day, count]) =>
            day.startsWith(`${today.getFullYear()}-`) && count > 0,
    ).length;
    const dates = Array.from({ length: 364 }, (_, index) => {
        const day = new Date(today);
        day.setDate(today.getDate() - 363 + index);
        const key = `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`;
        return { key, count: activity.edits[key] || 0 };
    });
    return (
        <section className="activity-panel">
            <div>
                <h3>{t("Hoạt động của bạn", "Your activity")}</h3>
                <span>
                    {Object.values(activity.edits).reduce(
                        (sum, value) => sum + value,
                        0,
                    )}{" "}
                    {t("lần lưu", "saves")} · {activity.compiles}{" "}
                    {t("lần biên dịch năm nay", "compiles this year")}
                </span>
            </div>
            <div className="activity-stats">
                <span>
                    {streak} {t("ngày liên tiếp", "day streak")}
                </span>
                <span>
                    {activeDays}{" "}
                    {t("ngày hoạt động trong năm", "active days this year")}
                </span>
            </div>
            <div className="heatmap">
                {dates.map(({ key, count }) => (
                    <span
                        key={key}
                        title={`${key}: ${count} ${t("lần lưu", "saves")}`}
                        data-level={Math.min(
                            4,
                            count === 0 ? 0 : 1 + Math.floor(Math.log2(count)),
                        )}
                    />
                ))}
            </div>
        </section>
    );
}
