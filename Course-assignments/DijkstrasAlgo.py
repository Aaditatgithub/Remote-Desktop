import pygame
import heapq

# Initialize pygame
pygame.init()

# Screen settings
WIDTH, HEIGHT = 800, 600
screen = pygame.display.set_mode((WIDTH, HEIGHT))
pygame.display.set_caption("Dijkstra Algorithm Simulation")

# Colors
WHITE = (255, 255, 255)
BLACK = (0, 0, 0)
GREEN = (0, 255, 0)
YELLOW = (255, 255, 0)

# Node positions
node_positions = {
    0: (100, 100),
    1: (300, 100),
    2: (500, 100),
    3: (300, 300),
    4: (500, 300),
    5: (700, 300)
}

# Graph represented as adjacency list
graph = {
    0: [(1, 7), (2, 9), (5, 14)],
    1: [(0, 7), (2, 10), (3, 15)],
    2: [(0, 9), (1, 10), (3, 11), (5, 2)],
    3: [(1, 15), (2, 11), (4, 6)],
    4: [(3, 6), (5, 9)],
    5: [(0, 14), (2, 2), (4, 9)]
}

# Dijkstra's algorithm
def dijkstra(start, graph):
    n = len(graph)
    distances = {node: float('infinity') for node in graph}
    previous_nodes = {node: None for node in graph}
    distances[start] = 0
    pq = [(0, start)]

    while pq:
        current_distance, current_node = heapq.heappop(pq)

        if current_distance > distances[current_node]:
            continue

        for neighbor, weight in graph[current_node]:
            distance = current_distance + weight

            if distance < distances[neighbor]:
                distances[neighbor] = distance
                previous_nodes[neighbor] = current_node
                heapq.heappush(pq, (distance, neighbor))
                yield neighbor, previous_nodes

# Visualize network
def draw_network(screen, node_positions, graph, current=None):
    screen.fill(BLACK)
    # Draw edges
    for node, edges in graph.items():
        for edge in edges:
            pygame.draw.line(screen, WHITE, node_positions[node], node_positions[edge[0]], 2)
    
    # Draw nodes
    for node, pos in node_positions.items():
        color = YELLOW if node == current else GREEN
        pygame.draw.circle(screen, color, pos, 20)
        label = pygame.font.SysFont(None, 30).render(f'R{node+1}', True, WHITE)
        screen.blit(label, (pos[0] - 10, pos[1] - 10))

# Main loop
def main():
    running = True
    start_node = 0
    dijkstra_gen = dijkstra(start_node, graph)
    
    while running:
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                running = False

        try:
            current_node, _ = next(dijkstra_gen)
        except StopIteration:
            current_node = None

        draw_network(screen, node_positions, graph, current_node)
        pygame.display.flip()
        pygame.time.delay(500)

    pygame.quit()

if __name__ == "__main__":
    main()
