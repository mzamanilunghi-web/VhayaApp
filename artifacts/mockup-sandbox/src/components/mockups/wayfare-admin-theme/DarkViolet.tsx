import "./_group.css";
import "./DarkViolet.css";
import { Current } from "./Current";

/**
 * A visual variant of the extracted operations overview.
 * Current remains the source of the page content, data, and interactions.
 */
export function DarkViolet() {
  return (
    <div className="dark-violet-preview">
      <Current />
    </div>
  );
}
