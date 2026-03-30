import { Link } from "react-router-dom";

export default function ProductCell({ code, name, showCode = false }) {
  const productCode = String(code || "").trim();
  const productName = String(name || "").trim() || productCode || "-";
  if (!productCode) {
    return productName;
  }
  const showMaterialCode = showCode && productName !== productCode;
  return (
    <Link
      className={`table-link${showMaterialCode ? " product-link" : ""}`}
      to={`/masterdata?product_code=${encodeURIComponent(productCode)}`}
    >
      <span>{productName}</span>
      {showMaterialCode ? <span className="product-code">{productCode}</span> : null}
    </Link>
  );
}
