package zxf.tunproxy.proxy.tcp;

import lombok.AllArgsConstructor;

public record Tuple<T, U>(T first, U second) {
}
