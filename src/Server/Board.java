package Server;

import java.util.HashMap;

public class Board {
    private HashMap<String, String>[] board;

    public Board(int size){
        board = new HashMap[size];
    }

    public void write(int idx, String u, String tag){
        if (board[idx] == null) {
            board[idx] = new HashMap<>();
        }
        board[idx].put(tag, u);
    }

    public HashMap<String, String> get(int idx){
        if (board.length < idx) return null;
        return board[idx];
    }

    public boolean isEmpty(){
        for (HashMap<String, String> cel : board){
            if (cel != null && !cel.isEmpty()){
                return false;
            }
        }
        return true;
    }
}
