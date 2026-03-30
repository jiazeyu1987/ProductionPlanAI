export default function RouteDetailsPanels({ processDetails, productDetails }) {
  return (
    <>
      {processDetails ? (
        <div className="panel">
          <h3>工序详情</h3>
          <div className="table-wrap">
            <table>
              <tbody>
                <tr>
                  <th>工序编码</th>
                  <td>{processDetails.processCode}</td>
                  <th>工序名称</th>
                  <td>{processDetails.processName}</td>
                </tr>
                <tr>
                  <th>涉及产品</th>
                  <td>{processDetails.products}</td>
                  <th>工艺步骤数</th>
                  <td>{processDetails.routeCount}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      ) : null}

      {productDetails ? (
        <div className="panel">
          <h3>产品工艺路线详情</h3>
          <div className="table-wrap">
            <table>
              <tbody>
                <tr>
                  <th>产品编码</th>
                  <td>{productDetails.productCode}</td>
                  <th>产品名称</th>
                  <td>{productDetails.productName}</td>
                </tr>
                <tr>
                  <th>路线编码</th>
                  <td>{productDetails.routeNo}</td>
                  <th>路线名称</th>
                  <td>{productDetails.routeName}</td>
                </tr>
                <tr>
                  <th>工艺步骤数</th>
                  <td>{productDetails.stepCount}</td>
                  <th>工序列表</th>
                  <td>{productDetails.steps}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      ) : null}
    </>
  );
}
